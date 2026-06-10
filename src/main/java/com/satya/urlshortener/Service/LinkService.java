package com.satya.urlshortener.Service;

import com.satya.urlshortener.Dto.CreateLinkRequest;
import com.satya.urlshortener.Dto.LinkAnalyticsResponse;
import com.satya.urlshortener.Dto.LinkResponse;
import com.satya.urlshortener.Entity.Link;
import com.satya.urlshortener.Entity.LinkClick;
import com.satya.urlshortener.Exception.AliasAlreadyInUseException;
import com.satya.urlshortener.Exception.ShortCodeExpiredException;
import com.satya.urlshortener.Exception.ShortCodeNotFoundException;
import com.satya.urlshortener.Repository.LinkClickRepository;
import com.satya.urlshortener.Repository.LinkRepository;
import com.satya.urlshortener.Util.Encoder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final UrlValidationService urlValidationService;
    private final Encoder encoder;
    private final LinkClickRepository linkClickRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ClickEventService clickEventService;
    private final BloomFilterService bloomFilterService;

    @Value("${app.baseUrl}")
    private String baseUrl;

    public LinkService(LinkRepository linkRepository,
                       LinkClickRepository linkClickRepository,
                       Encoder encoder,
                       UrlValidationService urlValidationService,
                       RedisTemplate<String, String> redisTemplate,
                       ClickEventService clickEventService,
                       BloomFilterService bloomFilterService) {
        this.linkRepository = linkRepository;
        this.urlValidationService = urlValidationService;
        this.encoder = encoder;
        this.linkClickRepository = linkClickRepository;
        this.redisTemplate = redisTemplate;
        this.clickEventService = clickEventService;
        this.bloomFilterService = bloomFilterService;
    }

    @Transactional
    public LinkResponse createShortUrl(CreateLinkRequest request) {
        urlValidationService.validateOriginalUrl(request.getOriginalUrl());

        Link link = new Link();
        link.setOriginalUrl(request.getOriginalUrl());
        link.setCreatedAt(LocalDateTime.now());

        if (request.getExpiresInDays() != null) {
            link.setExpiresAt(LocalDateTime.now().plusDays(request.getExpiresInDays()));
        }

        String shortCode;

        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            shortCode = request.getCustomAlias();
            urlValidationService.validateCustomAlias(shortCode);

            if (linkRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyInUseException(shortCode);
            }

            link.setShortCode(shortCode);
            link.setCustomAlias(true);
            linkRepository.save(link);

        } else {
            link.setShortCode("TEMP");
            link.setCustomAlias(false);
            link = linkRepository.save(link);
            shortCode = encoder.encode(link.getId());
            link.setShortCode(shortCode);
            linkRepository.save(link);
        }

        bloomFilterService.add(shortCode);

        redisTemplate.opsForValue().set(
                "link:" + shortCode,
                link.getId() + "::" + link.getOriginalUrl(),
                24,
                TimeUnit.HOURS
        );

        LinkResponse response = new LinkResponse();
        response.setShortCode(shortCode);
        response.setShortUrl(baseUrl + "/" + shortCode);
        response.setOriginalUrl(link.getOriginalUrl());
        response.setCreatedAt(link.getCreatedAt());
        response.setExpiresAt(link.getExpiresAt());

        return response;
    }

    public String redirectToOriginalUrl(String shortCode, HttpServletRequest httpRequest) {

        if (!bloomFilterService.mightExist(shortCode)) {
            throw new ShortCodeNotFoundException(shortCode);
        }

        String cacheKey = "link:" + shortCode;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);

        String ip = extractIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String referrer = httpRequest.getHeader("Referer");

        if (cachedValue != null) {
            String[] parts = cachedValue.split("::", 2);
            Long linkId = Long.parseLong(parts[0]);
            String originalUrl = parts[1];
            clickEventService.logClick(linkId, userAgent, referrer, ip);
            return originalUrl;
        }

        Link link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        // Grace period expiry check
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            LocalDateTime graceUntil = link.getGraceUntil();

            if (graceUntil != null && graceUntil.isBefore(LocalDateTime.now())) {
                // Grace period has ended — link is fully dead
                throw new ShortCodeNotFoundException(shortCode);
            }

            // Either in grace window, or scheduler hasn't run yet (graceUntil is null)
            // Either way: 410
            throw new ShortCodeExpiredException(shortCode);
        }

        redisTemplate.opsForValue().set(
                cacheKey,
                link.getId() + "::" + link.getOriginalUrl(),
                24,
                TimeUnit.HOURS
        );

        clickEventService.logClick(link.getId(), userAgent, referrer, ip);
        return link.getOriginalUrl();
    }

    @Transactional
    public void deleteLink(String shortCode) {
        Link link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        link.setDeletedAt(LocalDateTime.now());
        linkRepository.save(link);

        redisTemplate.delete("link:" + shortCode);
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public LinkAnalyticsResponse getAnalytics(String shortCode) {
        Link link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));

        LinkAnalyticsResponse response = new LinkAnalyticsResponse();
        response.setShortCode(link.getShortCode());
        response.setOriginalUrl(link.getOriginalUrl());
        response.setCreatedAt(link.getCreatedAt());
        response.setExpiresAt(link.getExpiresAt());
        response.setTotalClicks(linkClickRepository.countByLinkId(link.getId()));
        response.setExpired(link.getExpiresAt() != null &&
                link.getExpiresAt().isBefore(LocalDateTime.now()));

        linkClickRepository.findTopByLinkIdOrderByClickedAtDesc(link.getId())
                .map(LinkClick::getClickedAt)
                .ifPresent(response::setLastAccessed);

        return response;
    }
}