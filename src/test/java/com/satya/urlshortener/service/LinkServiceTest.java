package com.satya.urlshortener.service;

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
import com.satya.urlshortener.Service.BloomFilterService;
import com.satya.urlshortener.Service.ClickEventService;
import com.satya.urlshortener.Service.LinkService;
import com.satya.urlshortener.Service.UrlValidationService;
import com.satya.urlshortener.Util.Encoder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import io.micrometer.core.instrument.MeterRegistry;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LinkServiceTest {

    @Mock private LinkRepository linkRepository;
    @Mock private LinkClickRepository linkClickRepository;
    @Mock private UrlValidationService urlValidationService;
    @Mock private Encoder encoder;
    @Mock private HttpServletRequest httpRequest;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ClickEventService clickEventService;
    @Mock private BloomFilterService bloomFilterService;
    @Mock private ValueOperations<String, String> valueOperations;
    private final MeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    private LinkService linkService;

    @BeforeEach
    void setUp() {
        linkService = new LinkService(
                linkRepository,
                linkClickRepository,
                encoder,
                urlValidationService,
                redisTemplate,
                clickEventService,
                bloomFilterService,
                meterRegistry
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(bloomFilterService.mightExist(anyString())).thenReturn(true);
    }

    // ============= createShortUrl Tests =============

    @Test
    void createShortUrl_with_generated_shortcode_success() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/very-long-url");

        Link savedLink = Link.builder()
                .id(12345L).originalUrl("https://example.com/very-long-url")
                .shortCode("TEMP").customAlias(false).createdAt(LocalDateTime.now()).build();

        Link updatedLink = Link.builder()
                .id(12345L).originalUrl("https://example.com/very-long-url")
                .shortCode("xYz123").customAlias(false).createdAt(savedLink.getCreatedAt()).build();

        when(linkRepository.save(any(Link.class))).thenReturn(savedLink).thenReturn(updatedLink);
        when(encoder.encode(12345L)).thenReturn("xYz123");

        LinkResponse response = linkService.createShortUrl(request);

        assertThat(response).isNotNull()
                .extracting("shortCode", "originalUrl")
                .containsExactly("xYz123", "https://example.com/very-long-url");
        assertThat(response.getShortUrl()).contains("xYz123");

        verify(urlValidationService).validateOriginalUrl("https://example.com/very-long-url");
        verify(linkRepository, times(2)).save(any(Link.class));
        verify(encoder).encode(12345L);
        verify(bloomFilterService).add("xYz123");
        verify(valueOperations).set(
                eq("link:xYz123"),
                contains("https://example.com/very-long-url"),
                eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void createShortUrl_with_custom_alias_success() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/very-long-url");
        request.setCustomAlias("my-custom");

        Link savedLink = Link.builder()
                .id(999L).originalUrl("https://example.com/very-long-url")
                .shortCode("my-custom").customAlias(true).createdAt(LocalDateTime.now()).build();

        when(linkRepository.existsByShortCode("my-custom")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenReturn(savedLink);

        LinkResponse response = linkService.createShortUrl(request);

        assertThat(response.getShortCode()).isEqualTo("my-custom");
        assertThat(response.getOriginalUrl()).isEqualTo("https://example.com/very-long-url");

        verify(urlValidationService).validateOriginalUrl("https://example.com/very-long-url");
        verify(urlValidationService).validateCustomAlias("my-custom");
        verify(linkRepository).existsByShortCode("my-custom");
        verify(linkRepository, times(1)).save(any(Link.class));
        verify(bloomFilterService).add("my-custom");
        verify(valueOperations).set(
                eq("link:my-custom"),
                contains("https://example.com/very-long-url"),
                eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void createShortUrl_throws_exception_when_custom_alias_already_in_use() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/long-url");
        request.setCustomAlias("existing-alias");

        when(linkRepository.existsByShortCode("existing-alias")).thenReturn(true);

        assertThatThrownBy(() -> linkService.createShortUrl(request))
                .isInstanceOf(AliasAlreadyInUseException.class);

        verify(linkRepository, never()).save(any(Link.class));
        verify(bloomFilterService, never()).add(anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void createShortUrl_sets_expiresAt_when_expiresInDays_provided() {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/url");
        request.setExpiresInDays(7);

        Link savedLink = Link.builder()
                .id(555L).originalUrl("https://example.com/url")
                .shortCode("TEMP").customAlias(false)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now()).build();

        Link updatedLink = Link.builder()
                .id(555L).originalUrl("https://example.com/url")
                .shortCode("abc123").customAlias(false)
                .expiresAt(savedLink.getExpiresAt())
                .createdAt(savedLink.getCreatedAt()).build();

        when(linkRepository.save(any(Link.class))).thenReturn(savedLink).thenReturn(updatedLink);
        when(encoder.encode(555L)).thenReturn("abc123");

        LinkResponse response = linkService.createShortUrl(request);

        assertThat(response.getExpiresAt()).isNotNull();
        assertThat(response.getExpiresAt())
                .isAfter(LocalDateTime.now())
                .isBefore(LocalDateTime.now().plusDays(8));
    }

    // ============= redirectToOriginalUrl Tests =============

    @Test
    void redirectToOriginalUrl_returns_cached_url_on_cache_hit() {
        when(valueOperations.get("link:xYz123")).thenReturn("100::https://example.com/original");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(httpRequest.getHeader("Referer")).thenReturn("https://referrer.com");
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");

        String result = linkService.redirectToOriginalUrl("xYz123", httpRequest);

        assertThat(result).isEqualTo("https://example.com/original");
        verify(linkRepository, never()).findByShortCode(anyString());
        verify(clickEventService).logClick(eq(100L), anyString(), anyString(), anyString());
    }

    @Test
    void redirectToOriginalUrl_queries_postgres_on_cache_miss() {
        String shortCode = "xYz123";
        when(valueOperations.get("link:xYz123")).thenReturn(null);

        Link link = Link.builder()
                .id(100L).shortCode(shortCode)
                .originalUrl("https://example.com/original")
                .customAlias(false)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now()).build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(link));
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(httpRequest.getHeader("Referer")).thenReturn("https://referrer.com");
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");

        String result = linkService.redirectToOriginalUrl(shortCode, httpRequest);

        assertThat(result).isEqualTo("https://example.com/original");
        verify(linkRepository).findByShortCode(shortCode);
        verify(valueOperations).set(
                eq("link:xYz123"), eq("100::https://example.com/original"),
                eq(24L), eq(TimeUnit.HOURS));
        verify(clickEventService).logClick(eq(100L), anyString(), anyString(), anyString());
    }

    @Test
    void redirectToOriginalUrl_returns_404_immediately_when_bloom_filter_says_not_exist() {
        when(bloomFilterService.mightExist("zzzzzzz")).thenReturn(false);

        assertThatThrownBy(() -> linkService.redirectToOriginalUrl("zzzzzzz", httpRequest))
                .isInstanceOf(ShortCodeNotFoundException.class);

        verify(valueOperations, never()).get(anyString());
        verify(linkRepository, never()).findByShortCode(anyString());
        verify(clickEventService, never()).logClick(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void redirectToOriginalUrl_throws_exception_when_shortcode_not_found() {
        when(valueOperations.get("link:nonexistent")).thenReturn(null);
        when(linkRepository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.redirectToOriginalUrl("nonexistent", httpRequest))
                .isInstanceOf(ShortCodeNotFoundException.class);

        verify(clickEventService, never()).logClick(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void redirectToOriginalUrl_throws_exception_when_link_is_expired() {
        String shortCode = "expired123";
        when(valueOperations.get("link:expired123")).thenReturn(null);

        Link expiredLink = Link.builder()
                .id(200L).shortCode(shortCode)
                .originalUrl("https://example.com/expired")
                .customAlias(false)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .createdAt(LocalDateTime.now().minusDays(30)).build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(expiredLink));

        assertThatThrownBy(() -> linkService.redirectToOriginalUrl(shortCode, httpRequest))
                .isInstanceOf(ShortCodeExpiredException.class);

        verify(clickEventService, never()).logClick(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void redirectToOriginalUrl_does_not_log_click_on_expired_link() {
        String shortCode = "expired123";
        when(valueOperations.get("link:expired123")).thenReturn(null);

        Link expiredLink = Link.builder()
                .id(200L).shortCode(shortCode)
                .originalUrl("https://example.com/expired")
                .customAlias(false)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .createdAt(LocalDateTime.now().minusDays(30)).build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(expiredLink));

        assertThatThrownBy(() -> linkService.redirectToOriginalUrl(shortCode, httpRequest))
                .isInstanceOf(ShortCodeExpiredException.class);

        verify(clickEventService, never()).logClick(anyLong(), anyString(), anyString(), anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    // ============= getAnalytics Tests =============

    @Test
    void getAnalytics_returns_correct_total_clicks_count() {
        String shortCode = "analytics1";
        Link link = Link.builder()
                .id(400L).shortCode(shortCode)
                .originalUrl("https://example.com/analytics")
                .customAlias(false)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now()).build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(link));
        when(linkClickRepository.countByLinkId(400L)).thenReturn(3L);
        when(linkClickRepository.findTopByLinkIdOrderByClickedAtDesc(400L)).thenReturn(Optional.empty());

        LinkAnalyticsResponse response = linkService.getAnalytics(shortCode);

        assertThat(response.getTotalClicks()).isEqualTo(3);
        assertThat(response.getShortCode()).isEqualTo(shortCode);
        assertThat(response.getOriginalUrl()).isEqualTo("https://example.com/analytics");
    }

    @Test
    void getAnalytics_returns_correct_last_accessed_time() {
        String shortCode = "recent1";
        LocalDateTime now = LocalDateTime.now();
        Link link = Link.builder()
                .id(500L).shortCode(shortCode)
                .originalUrl("https://example.com/recent")
                .customAlias(false)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(now.minusHours(24)).build();

        LinkClick mostRecentClick = LinkClick.builder()
                .id(2L).link(link).clickedAt(now.minusMinutes(15)).build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(link));
        when(linkClickRepository.countByLinkId(500L)).thenReturn(2L);
        when(linkClickRepository.findTopByLinkIdOrderByClickedAtDesc(500L))
                .thenReturn(Optional.of(mostRecentClick));

        LinkAnalyticsResponse response = linkService.getAnalytics(shortCode);

        assertThat(response.getLastAccessed()).isEqualTo(now.minusMinutes(15));
    }

    @Test
    void getAnalytics_returns_isExpired_true_when_link_is_expired() {
        String shortCode = "expired_analytics";
        Link expiredLink = Link.builder()
                .id(600L).shortCode(shortCode)
                .originalUrl("https://example.com/expired")
                .customAlias(false)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .createdAt(LocalDateTime.now().minusDays(30)).build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(expiredLink));
        when(linkClickRepository.countByLinkId(600L)).thenReturn(0L);
        when(linkClickRepository.findTopByLinkIdOrderByClickedAtDesc(600L)).thenReturn(Optional.empty());

        LinkAnalyticsResponse response = linkService.getAnalytics(shortCode);

        assertThat(response.isExpired()).isTrue();
    }

    @Test
    void getAnalytics_returns_isExpired_false_when_link_is_not_expired() {
        String shortCode = "valid_analytics";
        Link validLink = Link.builder()
                .id(700L).shortCode(shortCode)
                .originalUrl("https://example.com/valid")
                .customAlias(false)
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now()).build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(validLink));
        when(linkClickRepository.countByLinkId(700L)).thenReturn(0L);
        when(linkClickRepository.findTopByLinkIdOrderByClickedAtDesc(700L)).thenReturn(Optional.empty());

        LinkAnalyticsResponse response = linkService.getAnalytics(shortCode);

        assertThat(response.isExpired()).isFalse();
    }

    @Test
    void getAnalytics_throws_exception_when_shortcode_not_found() {
        when(linkRepository.findByShortCode("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> linkService.getAnalytics("nonexistent"))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }
}