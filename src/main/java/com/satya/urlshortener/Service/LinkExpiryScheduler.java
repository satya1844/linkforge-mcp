package com.satya.urlshortener.Service;

import com.satya.urlshortener.Entity.Link;
import com.satya.urlshortener.Repository.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class LinkExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(LinkExpiryScheduler.class);

    private final LinkRepository linkRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public LinkExpiryScheduler(LinkRepository linkRepository,
                               RedisTemplate<String, String> redisTemplate) {
        this.linkRepository = linkRepository;
        this.redisTemplate = redisTemplate;
    }

    @Scheduled(cron = "0 0 2 * * *")   // 2am every night
    @Transactional
    public void processExpiredLinks() {
        LocalDateTime now = LocalDateTime.now();
        List<Link> newlyExpired = linkRepository.findByExpiresAtBeforeAndGraceUntilIsNull(now);

        if (newlyExpired.isEmpty()) {
            log.info("Link expiry scheduler: no newly expired links");
            return;
        }

        log.info("Link expiry scheduler: processing {} newly expired links", newlyExpired.size());

        for (Link link : newlyExpired) {
            int gracePeriodHours = link.getGracePeriodHours() != null
                    ? link.getGracePeriodHours()
                    : 24;

            link.setGraceUntil(link.getExpiresAt().plusHours(gracePeriodHours));
            linkRepository.save(link);

            // Evict from Redis — expired links must not be served from cache
            redisTemplate.delete("link:" + link.getShortCode());
            log.debug("Link expiry scheduler: set grace period for '{}', evicted from cache",
                    link.getShortCode());
        }

        log.info("Link expiry scheduler: done processing {} links", newlyExpired.size());
    }
}