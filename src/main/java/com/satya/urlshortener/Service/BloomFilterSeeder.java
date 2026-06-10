package com.satya.urlshortener.Service;

import com.satya.urlshortener.Repository.LinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BloomFilterSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BloomFilterSeeder.class);

    private final LinkRepository linkRepository;
    private final BloomFilterService bloomFilterService;

    public BloomFilterSeeder(LinkRepository linkRepository,
                             BloomFilterService bloomFilterService) {
        this.linkRepository = linkRepository;
        this.bloomFilterService = bloomFilterService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Bloom filter: seeding from database...");
        List<String> shortCodes = linkRepository.findAllActiveShortCodes();
        bloomFilterService.seedFromDatabase(shortCodes);
    }
}