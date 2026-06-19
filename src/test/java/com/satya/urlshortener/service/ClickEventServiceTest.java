package com.satya.urlshortener.service;

import com.satya.urlshortener.Entity.LinkClick;
import com.satya.urlshortener.Repository.LinkClickRepository;
import com.satya.urlshortener.Service.ClickEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClickEventServiceTest {

    @Mock
    private LinkClickRepository linkClickRepository;

    @InjectMocks
    private ClickEventService clickEventService;

    @Test
    void logClick_saves_click_event_with_expected_fields() {
        // Arrange
        Long linkId = 123L;
        String userAgent = "Mozilla/5.0";
        String referrer = "https://referrer.com";
        String ipAddress = "192.168.1.10";

        // Act
        clickEventService.logClick(linkId, userAgent, referrer, ipAddress);

        // Assert
        ArgumentCaptor<LinkClick> clickCaptor = ArgumentCaptor.forClass(LinkClick.class);
        verify(linkClickRepository).save(clickCaptor.capture());

        LinkClick savedClick = clickCaptor.getValue();
        assertThat(savedClick).isNotNull();
        assertThat(savedClick.getLink()).isNotNull();
        assertThat(savedClick.getLink().getId()).isEqualTo(linkId);
        assertThat(savedClick.getUserAgent()).isEqualTo(userAgent);
        assertThat(savedClick.getReferrer()).isEqualTo(referrer);
        assertThat(savedClick.getIpAddress()).isEqualTo(ipAddress);
        assertThat(savedClick.getClickedAt()).isNotNull();
    }
}
