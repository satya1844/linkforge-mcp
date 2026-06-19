package com.satya.urlshortener.service;

import com.satya.urlshortener.Entity.Link;
import com.satya.urlshortener.Repository.LinkRepository;
import com.satya.urlshortener.Service.LinkExpiryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkExpirySchedulerTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    private LinkExpiryScheduler linkExpiryScheduler;

    @BeforeEach
    void setUp() {
        linkExpiryScheduler = new LinkExpiryScheduler(linkRepository, redisTemplate);
    }

    @Test
    void processExpiredLinks_no_expired_links_does_nothing() {
        // Arrange
        when(linkRepository.findByExpiresAtBeforeAndGraceUntilIsNull(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // Act
        linkExpiryScheduler.processExpiredLinks();

        // Assert
        verify(linkRepository, never()).save(any(Link.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void processExpiredLinks_processes_expired_links_and_evicts_cache() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt1 = now.minusHours(2);
        LocalDateTime expiresAt2 = now.minusHours(5);

        Link link1 = Link.builder()
                .shortCode("link1")
                .expiresAt(expiresAt1)
                .gracePeriodHours(10)
                .build();

        Link link2 = Link.builder()
                .shortCode("link2")
                .expiresAt(expiresAt2)
                .gracePeriodHours(null) // Should default to 24 hours
                .build();

        when(linkRepository.findByExpiresAtBeforeAndGraceUntilIsNull(any(LocalDateTime.class)))
                .thenReturn(List.of(link1, link2));

        // Act
        linkExpiryScheduler.processExpiredLinks();

        // Assert
        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository, times(2)).save(linkCaptor.capture());

        List<Link> savedLinks = linkCaptor.getAllValues();
        assertThat(savedLinks).hasSize(2);

        Link saved1 = savedLinks.get(0);
        assertThat(saved1.getGraceUntil()).isEqualTo(expiresAt1.plusHours(10));

        Link saved2 = savedLinks.get(1);
        assertThat(saved2.getGraceUntil()).isEqualTo(expiresAt2.plusHours(24));

        verify(redisTemplate).delete("link:link1");
        verify(redisTemplate).delete("link:link2");
    }
}
