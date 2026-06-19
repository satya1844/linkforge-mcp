package com.satya.urlshortener.service;

import com.satya.urlshortener.Repository.LinkRepository;
import com.satya.urlshortener.Service.BloomFilterSeeder;
import com.satya.urlshortener.Service.BloomFilterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloomFilterSeederTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private BloomFilterService bloomFilterService;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private BloomFilterSeeder bloomFilterSeeder;

    @Test
    void run_seeds_bloom_filter_with_active_short_codes() {
        // Arrange
        List<String> activeShortCodes = List.of("code1", "code2", "code3");
        when(linkRepository.findAllActiveShortCodes()).thenReturn(activeShortCodes);

        // Act
        bloomFilterSeeder.run(applicationArguments);

        // Assert
        verify(linkRepository).findAllActiveShortCodes();
        verify(bloomFilterService).seedFromDatabase(activeShortCodes);
    }
}
