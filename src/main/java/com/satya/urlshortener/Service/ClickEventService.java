package com.satya.urlshortener.Service;

import com.satya.urlshortener.Entity.Link;
import com.satya.urlshortener.Entity.LinkClick;
import com.satya.urlshortener.Repository.LinkClickRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ClickEventService {

    private final LinkClickRepository linkClickRepository;

    public ClickEventService(LinkClickRepository linkClickRepository) {
        this.linkClickRepository = linkClickRepository;
    }

    @Async
    @Transactional
    public void logClick(Long linkId, String userAgent, String referrer, String ipAddress) {
        LinkClick click = new LinkClick();

        // We only need the ID for the foreign key — no need to fetch the full Link object
        Link link = new Link();
        link.setId(linkId);

        click.setLink(link);
        click.setClickedAt(LocalDateTime.now());
        click.setUserAgent(userAgent);
        click.setReferrer(referrer);
        click.setIpAddress(ipAddress);

        linkClickRepository.save(click);
    }
}