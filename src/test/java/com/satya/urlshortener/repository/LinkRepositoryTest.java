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
        linkClickRepository.deleteAll();
        linkRepository.deleteAll();
    }

    // ============= LinkRepository Tests =============

    @Test
    @Transactional
    void find_by_shortcode_returns_link_when_found() {
        Link link = Link.builder()
                .shortCode("abc123")
                .originalUrl("https://example.com/test")
                .customAlias(false)
                .createdAt(LocalDateTime.now())
                .build();
        Link savedLink = linkRepository.save(link);

        Optional<Link> found = linkRepository.findByShortCode("abc123");

        assertThat(found)
                .isPresent()
                .contains(savedLink)
                .get()
                .extracting("shortCode", "originalUrl")
                .containsExactly("abc123", "https://example.com/test");
    }

    @Test
    void find_by_shortcode_returns_empty_when_not_found() {
        Optional<Link> found = linkRepository.findByShortCode("nonexistent");
        assertThat(found).isEmpty();
    }

    @Test
    @Transactional
    void exists_by_shortcode_returns_true_when_exists() {
        Link link = Link.builder()
                .shortCode("exists123")
                .originalUrl("https://example.com/exists")
                .customAlias(false)
                .createdAt(LocalDateTime.now())
                .build();
        linkRepository.save(link);

        boolean exists = linkRepository.existsByShortCode("exists123");
        assertThat(exists).isTrue();
    }

    @Test
    void exists_by_shortcode_returns_false_when_not_exists() {
        boolean exists = linkRepository.existsByShortCode("doesnotexist");
        assertThat(exists).isFalse();
    }

    // ============= LinkClickRepository Tests =============

    @Test
    @Transactional
    void find_by_link_id_returns_all_clicks_for_link() {
        Link link = Link.builder()
                .shortCode("tracked123")
                .originalUrl("https://example.com/track")
                .customAlias(false)
                .createdAt(LocalDateTime.now())
                .build();
        Link savedLink = linkRepository.save(link);

        linkClickRepository.save(LinkClick.builder()
                .link(savedLink).clickedAt(LocalDateTime.now().minusHours(2))
                .ipAddress("192.168.1.1").userAgent("Browser1").build());
        linkClickRepository.save(LinkClick.builder()
                .link(savedLink).clickedAt(LocalDateTime.now().minusHours(1))
                .ipAddress("192.168.1.2").userAgent("Browser2").build());
        linkClickRepository.save(LinkClick.builder()
                .link(savedLink).clickedAt(LocalDateTime.now())
                .ipAddress("192.168.1.3").userAgent("Browser3").build());

        List<LinkClick> clicks = linkClickRepository.findByLinkId(savedLink.getId());

        assertThat(clicks)
                .hasSize(3)
                .extracting("ipAddress")
                .containsExactlyInAnyOrder("192.168.1.1", "192.168.1.2", "192.168.1.3");
    }

    @Test
    @Transactional
    void find_by_link_id_returns_empty_list_when_no_clicks_exist() {
        Link link = Link.builder()
                .shortCode("notrack123")
                .originalUrl("https://example.com/notrack")
                .customAlias(false)
                .createdAt(LocalDateTime.now())
                .build();
        Link savedLink = linkRepository.save(link);

        List<LinkClick> clicks = linkClickRepository.findByLinkId(savedLink.getId());
        assertThat(clicks).isEmpty();
    }

    @Test
    @Transactional
    void find_by_link_id_returns_only_clicks_for_specific_link() {
        Link savedLink1 = linkRepository.save(Link.builder()
                .shortCode("link1").originalUrl("https://example.com/link1")
                .customAlias(false).createdAt(LocalDateTime.now()).build());

        Link savedLink2 = linkRepository.save(Link.builder()
                .shortCode("link2").originalUrl("https://example.com/link2")
                .customAlias(false).createdAt(LocalDateTime.now()).build());

        linkClickRepository.save(LinkClick.builder()
                .link(savedLink1).clickedAt(LocalDateTime.now()).ipAddress("1.1.1.1").build());
        linkClickRepository.save(LinkClick.builder()
                .link(savedLink2).clickedAt(LocalDateTime.now()).ipAddress("2.2.2.2").build());
        linkClickRepository.save(LinkClick.builder()
                .link(savedLink1).clickedAt(LocalDateTime.now()).ipAddress("1.1.1.2").build());

        List<LinkClick> clicks = linkClickRepository.findByLinkId(savedLink1.getId());

        assertThat(clicks)
                .hasSize(2)
                .extracting("ipAddress")
                .containsExactlyInAnyOrder("1.1.1.1", "1.1.1.2");
    }
}