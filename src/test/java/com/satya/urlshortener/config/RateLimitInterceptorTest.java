package com.satya.urlshortener.config;

import com.satya.urlshortener.Config.RateLimitInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitInterceptorTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Object handler;

    private RateLimitInterceptor rateLimitInterceptor;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitInterceptor = new RateLimitInterceptor(redisTemplate);
    }

    @Test
    void preHandle_localhost_ip_bypasses_limits() throws Exception {
        // Arrange
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Act
        boolean result = rateLimitInterceptor.preHandle(request, response, handler);

        // Assert
        assertThat(result).isTrue();
        verifyNoInteractions(valueOperations);
    }

    @Test
    void preHandle_post_urls_without_api_key_limits_by_ip() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/urls");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.10.20"); // Non-localhost
        when(request.getHeader("X-API-Key")).thenReturn(null); // No API key

        String rateKey = "rate:create:ip:192.168.10.20";
        when(valueOperations.increment(rateKey)).thenReturn(1L);

        // Act
        boolean result = rateLimitInterceptor.preHandle(request, response, handler);

        // Assert
        assertThat(result).isTrue();
        verify(redisTemplate).expire(eq(rateKey), any(Duration.class));
    }

    @Test
    void preHandle_post_urls_with_api_key_under_limit_succeeds() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/urls");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.10.20");
        when(request.getHeader("X-API-Key")).thenReturn("my-secret-key");

        String rateKey = "rate:create:my-secret-key";
        when(valueOperations.increment(rateKey)).thenReturn(1L);

        // Act
        boolean result = rateLimitInterceptor.preHandle(request, response, handler);

        // Assert
        assertThat(result).isTrue();
        verify(redisTemplate).expire(eq(rateKey), any(Duration.class));
    }

    @Test
    void preHandle_post_urls_with_api_key_exceeding_limit_returns_429() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/urls");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("192.168.10.20");
        when(request.getHeader("X-API-Key")).thenReturn("my-secret-key");

        String rateKey = "rate:create:my-secret-key";
        when(valueOperations.increment(rateKey)).thenReturn(21L); // limit is 20
        when(redisTemplate.getExpire(eq(rateKey), any())).thenReturn(45L);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        when(response.getWriter()).thenReturn(pw);

        // Act
        boolean result = rateLimitInterceptor.preHandle(request, response, handler);

        // Assert
        assertThat(result).isFalse();
        verify(response).setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        verify(response).setHeader("Retry-After", "45");
        verify(response).setContentType("application/json");
        assertThat(sw.toString()).contains("Rate limit exceeded");
    }

    @Test
    void preHandle_get_redirect_under_limit_succeeds() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/abc123");
        when(request.getHeader("X-Forwarded-For")).thenReturn("172.56.21.9");

        String rateKey = "rate:redirect:172.56.21.9";
        when(valueOperations.increment(rateKey)).thenReturn(5L);

        // Act
        boolean result = rateLimitInterceptor.preHandle(request, response, handler);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void preHandle_get_analytics_bypasses_redirect_limits() throws Exception {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/urls/abc123/analytics");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("172.56.21.9");

        // Act
        boolean result = rateLimitInterceptor.preHandle(request, response, handler);

        // Assert
        assertThat(result).isTrue();
        verifyNoInteractions(valueOperations);
    }
}
