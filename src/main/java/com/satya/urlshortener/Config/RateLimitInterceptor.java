package com.satya.urlshortener.Config;

// GET /{shortCode} — rate limit by IP
// Exclude /urls/** (analytics) from redirect rate limiting
// GET /urls/{shortCode}/analytics — skip (not a redirect)
// GET /urls/{shortCode}           — rate limit by IP
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final int  CREATE_LIMIT        = 20;
    private static final int  REDIRECT_LIMIT      = 100;
    private static final long WINDOW_SECONDS       = 60L;
    private final RedisTemplate<String, String> redisTemplate;

    public RateLimitInterceptor(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private boolean isLocalhost(HttpServletRequest request) {
        String ip = extractIp(request);
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip);
    }




    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String method = request.getMethod();
        String uri    = request.getRequestURI();

        if (isLocalhost(request)) {
            return true;
        }
        // POST /urls — rate limit by API key
        if ("POST".equalsIgnoreCase(method) && uri.startsWith("/urls")) {
            String apiKey = request.getHeader("X-API-Key");
            if (apiKey == null || apiKey.isBlank()) {
                // No API key — auth filter handles this, just pass through here
                return true;
            }
            return checkLimit(
                    "rate:create:" + apiKey,
                    CREATE_LIMIT,
                    response
            );
        }



        if ("GET".equalsIgnoreCase(method) && uri.startsWith("/urls/")) {
            boolean isAnalytics = uri.matches("/urls/[^/]+/analytics");
            if (!isAnalytics) {
                String ip = extractIp(request);
                return checkLimit("rate:redirect:" + ip, REDIRECT_LIMIT, response);
            }
        }

        return true;
    }

    private boolean checkLimit(String key, int limit, HttpServletResponse response) throws Exception {
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            // Redis error — fail open, don't block the request
            log.warn("Rate limiter: Redis returned null for key '{}', failing open", key);
            return true;
        }
        // First request — set the expiry window
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS));
        }

        if (count > limit) {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfter = (ttl != null && ttl > 0) ? ttl : WINDOW_SECONDS;

            log.debug("Rate limit exceeded for key '{}': count={}, limit={}", key, count, limit);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write(String.format("""
                    {
                      "status": 429,
                      "error": "Rate limit exceeded",
                      "message": "Too many requests. Limit resets in %d seconds.",
                      "retryAfter": %d
                    }""", retryAfter, retryAfter));
            return false;
        }

        return true;
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}