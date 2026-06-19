package com.satya.urlshortener.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.satya.urlshortener.Dto.CreateLinkRequest;
import com.satya.urlshortener.Entity.Link;
import com.satya.urlshortener.Entity.LinkClick;
import com.satya.urlshortener.Repository.LinkClickRepository;
import com.satya.urlshortener.Repository.LinkRepository;
import com.satya.urlshortener.Service.LinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
public class LinkIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("urlshortener_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("app.baseUrl", () -> "http://localhost");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private LinkService linkService;

    @MockitoSpyBean
    private LinkRepository linkRepository;

    @Autowired
    private LinkClickRepository linkClickRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Clean database and Redis
        linkClickRepository.deleteAll();
        linkRepository.deleteAll();

        java.util.Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void secondRedirectShouldNotHitPostgres() throws Exception {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://example.com/cache-test");

        String responseJson = mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String shortCode = objectMapper.readTree(responseJson).get("shortCode").asText();

        // Hit redirect once (this checks cache and saves to cache if miss)
        mockMvc.perform(get("/urls/" + shortCode))
                .andExpect(status().isFound());

        // Now, delete the link directly from the repository database, but NOT from cache
        // We bypass the service's delete method so we don't evict from cache
        Link link = linkRepository.findByShortCode(shortCode).orElseThrow();
        linkClickRepository.deleteAll(); // clear foreign keys
        linkRepository.delete(link);

        // Hit redirect again — should still succeed (302 Found) because it's served from Redis!
        mockMvc.perform(get("/urls/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/cache-test"));
    }

    @Test
    void redisKeyExistsAfterFirstRedirect() throws Exception {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://example.com/redis-key-test");

        String responseJson = mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String shortCode = objectMapper.readTree(responseJson).get("shortCode").asText();

        // Key should exist in Redis after creation
        String cacheKey = "link:" + shortCode;
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        String cachedValue = redisTemplate.opsForValue().get(cacheKey);
        assertThat(cachedValue).contains("https://example.com/redis-key-test");
    }

    @Test
    void linkDeletionRemovesRedisKey() throws Exception {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://example.com/delete-test");

        String responseJson = mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String shortCode = objectMapper.readTree(responseJson).get("shortCode").asText();
        String cacheKey = "link:" + shortCode;

        // Verify key exists
        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();

        // Delete the link via LinkService which deletes from DB and evicts cache
        linkService.deleteLink(shortCode);

        // Verify key is gone
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();
    }

    @Test
    void unknownShortCodeReturns404WithoutDbQuery() throws Exception {
        // Query an unknown short code
        mockMvc.perform(get("/urls/nonexistentcode"))
                .andExpect(status().isNotFound());

        // Verify that linkRepository findByShortCode was never called because Bloom filter intercepted it
        verify(linkRepository, never()).findByShortCode("nonexistentcode");
    }

    @Test
    void rateLimit_create_returns_429_too_many_requests() throws Exception {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://example.com/ratelimit-create");

        // 10 creations should succeed
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/urls")
                            .header("X-API-Key", "test-api-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isOk());
        }

        // 11th creation should return 429
        mockMvc.perform(post("/urls")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void rateLimit_redirect_returns_429_too_many_requests() throws Exception {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://example.com/ratelimit-redirect");

        String responseJson = mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String shortCode = objectMapper.readTree(responseJson).get("shortCode").asText();

        // 60 redirects should succeed
        for (int i = 0; i < 60; i++) {
            mockMvc.perform(get("/urls/" + shortCode)
                            .header("X-Forwarded-For", "1.2.3.4"))
                    .andExpect(status().isFound());
        }

        // 61st redirect should fail with 429
        mockMvc.perform(get("/urls/" + shortCode)
                        .header("X-Forwarded-For", "1.2.3.4"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void clickRowExistsAfterRedirectCompletes() throws Exception {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://example.com/async-click");

        String responseJson = mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String shortCode = objectMapper.readTree(responseJson).get("shortCode").asText();
        Link link = linkRepository.findByShortCode(shortCode).orElseThrow();

        // Perform redirect
        mockMvc.perform(get("/urls/" + shortCode)
                        .header("User-Agent", "Mozilla/5.0 Test")
                        .header("Referer", "http://test-ref.com")
                        .header("X-Forwarded-For", "5.6.7.8"))
                .andExpect(status().isFound());

        // Wait a short time for async event execution
        Thread.sleep(150);

        // Verify click row exists
        List<LinkClick> clicks = linkClickRepository.findByLinkId(link.getId());
        assertThat(clicks).hasSize(1);
        LinkClick click = clicks.get(0);
        assertThat(click.getUserAgent()).isEqualTo("Mozilla/5.0 Test");
        assertThat(click.getReferrer()).isEqualTo("http://test-ref.com");
        assertThat(click.getIpAddress()).isEqualTo("5.6.7.8");
    }
}
