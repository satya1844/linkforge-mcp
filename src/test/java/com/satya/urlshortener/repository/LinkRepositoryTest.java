package com.satya.urlshortener.repository;

import com.satya.urlshortener.Entity.Link;
import com.satya.urlshortener.Entity.LinkClick;
import com.satya.urlshortener.Repository.LinkClickRepository;
import com.satya.urlshortener.Repository.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LinkRepositoryTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("urlshortener_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private LinkRepository linkRepository;

    @Autowired
    private LinkClickRepository linkClickRepository;

    @BeforeEach
    void setUp() {
        linkRepository.deleteAll();
        linkClickRepository.deleteAll();
    }

    // ============= LinkRepository Tests =============

    @Test
    @Transactional
    void find_by_shortcode_returns_link_when_found() {
        // Arrange
        Link link = Link.builder()
                .shortCode("abc123")
                .originalUrl("https://example.com/test")
                .createdAt(LocalDateTime.now())
                .build();
        Link savedLink = linkRepository.save(link);

        // Act
        Optional<Link> found = linkRepository.findByShortCode("abc123");

        // Assert
        assertThat(found)
                .isPresent()
                .contains(savedLink)
                .get()
                .extracting("shortCode", "originalUrl")
                .containsExactly("abc123", "https://example.com/test");
    }

    @Test
    void find_by_shortcode_returns_empty_when_not_found() {
        // Act
        Optional<Link> found = linkRepository.findByShortCode("nonexistent");

        // Assert
        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void exists_by_shortcode_returns_true_when_exists() {
        // Arrange
        Link link = Link.builder()
                .shortCode("exists123")
                .originalUrl("https://example.com/exists")
                .createdAt(LocalDateTime.now())
                .build();
        linkRepository.save(link);

        // Act
        boolean exists = linkRepository.existsByShortCode("exists123");

        // Assert
        assertThat(exists).isTrue();
    }

    @Test
    void exists_by_shortcode_returns_false_when_not_exists() {
        // Act
        boolean exists = linkRepository.existsByShortCode("doesnotexist");

        // Assert
        assertThat(exists).isFalse();
    }

    // ============= LinkClickRepository Tests =============

    @Test
    @Transactional
    void find_by_link_id_returns_all_clicks_for_link() {
        // Arrange
        Link link = Link.builder()
                .shortCode("tracked123")
                .originalUrl("https://example.com/track")
                .createdAt(LocalDateTime.now())
                .build();
        Link savedLink = linkRepository.save(link);

        LinkClick click1 = LinkClick.builder()
                .link(savedLink)
                .clickedAt(LocalDateTime.now().minusHours(2))
                .ipAddress("192.168.1.1")
                .userAgent("Browser1")
                .build();

        LinkClick click2 = LinkClick.builder()
                .link(savedLink)
                .clickedAt(LocalDateTime.now().minusHours(1))
                .ipAddress("192.168.1.2")
                .userAgent("Browser2")
                .build();

        LinkClick click3 = LinkClick.builder()
                .link(savedLink)
                .clickedAt(LocalDateTime.now())
                .ipAddress("192.168.1.3")
                .userAgent("Browser3")
                .build();

        linkClickRepository.save(click1);
        linkClickRepository.save(click2);
        linkClickRepository.save(click3);

        // Act
        List<LinkClick> clicks = linkClickRepository.findByLinkId(savedLink.getId());

        // Assert
        assertThat(clicks)
                .hasSize(3)
                .extracting("ipAddress")
                .containsExactlyInAnyOrder("192.168.1.1", "192.168.1.2", "192.168.1.3");
    }

    @Test
    @Transactional
    void find_by_link_id_returns_empty_list_when_no_clicks_exist() {
        // Arrange
        Link link = Link.builder()
                .shortCode("notrack123")
                .originalUrl("https://example.com/notrack")
                .createdAt(LocalDateTime.now())
                .build();
        Link savedLink = linkRepository.save(link);

        // Act
        List<LinkClick> clicks = linkClickRepository.findByLinkId(savedLink.getId());

        // Assert
        assertThat(clicks).isEmpty();
    }

    @Test
    @Transactional
    void find_by_link_id_returns_only_clicks_for_specific_link() {
        // Arrange
        Link link1 = Link.builder()
                .shortCode("link1")
                .originalUrl("https://example.com/link1")
                .createdAt(LocalDateTime.now())
                .build();

        Link link2 = Link.builder()
                .shortCode("link2")
                .originalUrl("https://example.com/link2")
                .createdAt(LocalDateTime.now())
                .build();

        Link savedLink1 = linkRepository.save(link1);
        Link savedLink2 = linkRepository.save(link2);

        LinkClick click1 = LinkClick.builder()
                .link(savedLink1)
                .clickedAt(LocalDateTime.now())
                .ipAddress("1.1.1.1")
                .build();

        LinkClick click2 = LinkClick.builder()
                .link(savedLink2)
                .clickedAt(LocalDateTime.now())
                .ipAddress("2.2.2.2")
                .build();

        LinkClick click3 = LinkClick.builder()
                .link(savedLink1)
                .clickedAt(LocalDateTime.now())
                .ipAddress("1.1.1.2")
                .build();

        linkClickRepository.save(click1);
        linkClickRepository.save(click2);
        linkClickRepository.save(click3);

        // Act
        List<LinkClick> clicks = linkClickRepository.findByLinkId(savedLink1.getId());

        // Assert
        assertThat(clicks)
                .hasSize(2)
                .extracting("ipAddress")
                .containsExactlyInAnyOrder("1.1.1.1", "1.1.1.2");
    }
}
