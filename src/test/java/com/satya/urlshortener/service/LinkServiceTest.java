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
import com.satya.urlshortener.Service.LinkService;
import com.satya.urlshortener.Service.UrlValidationService;
import com.satya.urlshortener.Util.Encoder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private LinkClickRepository linkClickRepository;

    @Mock
    private UrlValidationService urlValidationService;

    @Mock
    private Encoder encoder;

    @Mock
    private HttpServletRequest httpRequest;

    private LinkService linkService;

    @BeforeEach
    void setUp() {
        linkService = new LinkService(linkRepository, linkClickRepository, encoder, urlValidationService);
    }

    // ============= createShortUrl Tests =============

    @Test
    void createShortUrl_with_generated_shortcode_success() {
        // Arrange
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/very-long-url");

        Link savedLink = Link.builder()
                .id(12345L)
                .originalUrl("https://example.com/very-long-url")
                .shortCode("TEMP")
                .createdAt(LocalDateTime.now())
                .build();

        Link updatedLink = Link.builder()
                .id(12345L)
                .originalUrl("https://example.com/very-long-url")
                .shortCode("xYz123")
                .createdAt(savedLink.getCreatedAt())
                .build();

        when(linkRepository.save(any(Link.class)))
                .thenReturn(savedLink)
                .thenReturn(updatedLink);
        when(encoder.encode(12345L)).thenReturn("xYz123");

        // Act
        LinkResponse response = linkService.createShortUrl(request);

        // Assert
        assertThat(response)
                .isNotNull()
                .extracting("shortCode", "originalUrl")
                .containsExactly("xYz123", "https://example.com/very-long-url");
        assertThat(response.getShortUrl()).contains("xYz123");

        verify(urlValidationService).validateOriginalUrl("https://example.com/very-long-url");
        verify(linkRepository, times(2)).save(any(Link.class));
        verify(encoder).encode(12345L);
    }

    @Test
    void createShortUrl_with_custom_alias_success() {
        // Arrange
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/very-long-url");
        request.setCustomAlias("my-custom");

        Link savedLink = Link.builder()
                .id(999L)
                .originalUrl("https://example.com/very-long-url")
                .shortCode("my-custom")
                .createdAt(LocalDateTime.now())
                .build();

        when(linkRepository.existsByShortCode("my-custom")).thenReturn(false);
        when(linkRepository.save(any(Link.class))).thenReturn(savedLink);

        // Act
        LinkResponse response = linkService.createShortUrl(request);

        // Assert
        assertThat(response.getShortCode()).isEqualTo("my-custom");
        assertThat(response.getOriginalUrl()).isEqualTo("https://example.com/very-long-url");

        verify(urlValidationService).validateOriginalUrl("https://example.com/very-long-url");
        verify(urlValidationService).validateCustomAlias("my-custom");
        verify(linkRepository).existsByShortCode("my-custom");
        verify(linkRepository, times(1)).save(any(Link.class));
    }

    @Test
    void createShortUrl_throws_exception_when_custom_alias_already_in_use() {
        // Arrange
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/long-url");
        request.setCustomAlias("existing-alias");

        when(linkRepository.existsByShortCode("existing-alias")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> linkService.createShortUrl(request))
                .isInstanceOf(AliasAlreadyInUseException.class);

        verify(urlValidationService).validateOriginalUrl("https://example.com/long-url");
        verify(urlValidationService).validateCustomAlias("existing-alias");
        verify(linkRepository).existsByShortCode("existing-alias");
        verify(linkRepository, never()).save(any(Link.class));
    }

    @Test
    void createShortUrl_sets_expiresAt_when_expiresInDays_provided() {
        // Arrange
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/url");
        request.setExpiresInDays(7);

        Link savedLink = Link.builder()
                .id(555L)
                .originalUrl("https://example.com/url")
                .shortCode("TEMP")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .createdAt(LocalDateTime.now())
                .build();

        Link updatedLink = Link.builder()
                .id(555L)
                .originalUrl("https://example.com/url")
                .shortCode("abc123")
                .expiresAt(savedLink.getExpiresAt())
                .createdAt(savedLink.getCreatedAt())
                .build();

        when(linkRepository.save(any(Link.class)))
                .thenReturn(savedLink)
                .thenReturn(updatedLink);
        when(encoder.encode(555L)).thenReturn("abc123");

        // Act
        LinkResponse response = linkService.createShortUrl(request);

        // Assert
        assertThat(response.getExpiresAt()).isNotNull();
        assertThat(response.getExpiresAt())
                .isAfter(LocalDateTime.now())
                .isBefore(LocalDateTime.now().plusDays(8));
    }

    // ============= redirectToOriginalUrl Tests =============

    @Test
    void redirectToOriginalUrl_returns_original_url_for_valid_shortcode() {
        // Arrange
        String shortCode = "xYz123";
        Link link = Link.builder()
                .id(100L)
                .shortCode(shortCode)
                .originalUrl("https://example.com/original")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(link));
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(httpRequest.getHeader("Referer")).thenReturn("https://referrer.com");
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.100");

        // Act
        String result = linkService.redirectToOriginalUrl(shortCode, httpRequest);

        // Assert
        assertThat(result).isEqualTo("https://example.com/original");
        verify(linkClickRepository).save(argThat(click ->
                click.getLink().getId().equals(100L) &&
                click.getUserAgent().equals("Mozilla/5.0") &&
                click.getIpAddress().equals("192.168.1.100")
        ));
    }

    @Test
    void redirectToOriginalUrl_throws_exception_when_shortcode_not_found() {
        // Arrange
        String shortCode = "nonexistent";
        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> linkService.redirectToOriginalUrl(shortCode, httpRequest))
                .isInstanceOf(ShortCodeNotFoundException.class);

        verify(linkClickRepository, never()).save(any(LinkClick.class));
    }

    @Test
    void redirectToOriginalUrl_throws_exception_when_link_is_expired() {
        // Arrange
        String shortCode = "expired123";
        Link expiredLink = Link.builder()
                .id(200L)
                .shortCode(shortCode)
                .originalUrl("https://example.com/expired")
                .expiresAt(LocalDateTime.now().minusHours(1))
                .createdAt(LocalDateTime.now().minusDays(30))
                .build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(expiredLink));

        // Act & Assert
        assertThatThrownBy(() -> linkService.redirectToOriginalUrl(shortCode, httpRequest))
                .isInstanceOf(ShortCodeExpiredException.class);

        verify(linkClickRepository, never()).save(any(LinkClick.class));
    }

    @Test
    void redirectToOriginalUrl_saves_link_click_on_successful_redirect() {
        // Arrange
        String shortCode = "tracked123";
        Link link = Link.builder()
                .id(300L)
                .shortCode(shortCode)
                .originalUrl("https://example.com/track")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(link));
        when(httpRequest.getHeader("User-Agent")).thenReturn("TestBrowser/1.0");
        when(httpRequest.getHeader("Referer")).thenReturn(null);
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");

        // Act
        linkService.redirectToOriginalUrl(shortCode, httpRequest);

        // Assert
        verify(linkClickRepository).save(argThat(click ->
                click.getLink().getId().equals(300L) &&
                click.getUserAgent().equals("TestBrowser/1.0") &&
                click.getClickedAt() != null
        ));
    }

    // ============= getAnalytics Tests =============

    @Test
    void getAnalytics_returns_correct_total_clicks_count() {
        // Arrange
        String shortCode = "analytics1";
        Link link = Link.builder()
                .id(400L)
                .shortCode(shortCode)
                .originalUrl("https://example.com/analytics")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build();

        LinkClick click1 = LinkClick.builder()
                .id(1L)
                .link(link)
                .clickedAt(LocalDateTime.now().minusHours(2))
                .ipAddress("192.168.1.1")
                .userAgent("Browser1")
                .build();

        LinkClick click2 = LinkClick.builder()
                .id(2L)
                .link(link)
                .clickedAt(LocalDateTime.now().minusHours(1))
                .ipAddress("192.168.1.2")
                .userAgent("Browser2")
                .build();

        LinkClick click3 = LinkClick.builder()
                .id(3L)
                .link(link)
                .clickedAt(LocalDateTime.now().minusMinutes(30))
                .ipAddress("192.168.1.3")
                .userAgent("Browser3")
                .build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(link));
        when(linkClickRepository.findByLinkId(400L)).thenReturn(List.of(click1, click2, click3));

        // Act
        LinkAnalyticsResponse response = linkService.getAnalytics(shortCode);

        // Assert
        assertThat(response.getTotalClicks()).isEqualTo(3);
        assertThat(response.getShortCode()).isEqualTo(shortCode);
        assertThat(response.getOriginalUrl()).isEqualTo("https://example.com/analytics");
    }

    @Test
    void getAnalytics_returns_correct_last_accessed_time() {
        // Arrange
        String shortCode = "recent1";
        LocalDateTime now = LocalDateTime.now();
        Link link = Link.builder()
                .id(500L)
                .shortCode(shortCode)
                .originalUrl("https://example.com/recent")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(now.minusHours(24))
                .build();

        LinkClick click1 = LinkClick.builder()
                .id(1L)
                .link(link)
                .clickedAt(now.minusHours(5))
                .build();

        LinkClick click2 = LinkClick.builder()
                .id(2L)
                .link(link)
                .clickedAt(now.minusMinutes(15))
                .build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(link));
        when(linkClickRepository.findByLinkId(500L)).thenReturn(List.of(click1, click2));

        // Act
        LinkAnalyticsResponse response = linkService.getAnalytics(shortCode);

        // Assert
        assertThat(response.getLastAccessed()).isEqualTo(now.minusMinutes(15));
    }

    @Test
    void getAnalytics_returns_isExpired_true_when_link_is_expired() {
        // Arrange
        String shortCode = "expired_analytics";
        Link expiredLink = Link.builder()
                .id(600L)
                .shortCode(shortCode)
                .originalUrl("https://example.com/expired")
                .expiresAt(LocalDateTime.now().minusHours(1))
                .createdAt(LocalDateTime.now().minusDays(30))
                .build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(expiredLink));
        when(linkClickRepository.findByLinkId(600L)).thenReturn(List.of());

        // Act
        LinkAnalyticsResponse response = linkService.getAnalytics(shortCode);

        // Assert
        assertThat(response.isExpired()).isTrue();
    }

    @Test
    void getAnalytics_returns_isExpired_false_when_link_is_not_expired() {
        // Arrange
        String shortCode = "valid_analytics";
        Link validLink = Link.builder()
                .id(700L)
                .shortCode(shortCode)
                .originalUrl("https://example.com/valid")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .build();

        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.of(validLink));
        when(linkClickRepository.findByLinkId(700L)).thenReturn(List.of());

        // Act
        LinkAnalyticsResponse response = linkService.getAnalytics(shortCode);

        // Assert
        assertThat(response.isExpired()).isFalse();
    }

    @Test
    void getAnalytics_throws_exception_when_shortcode_not_found() {
        // Arrange
        String shortCode = "nonexistent";
        when(linkRepository.findByShortCode(shortCode)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> linkService.getAnalytics(shortCode))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }
}
