package com.satya.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.satya.urlshortener.Dto.CreateLinkRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
class LinkControllerTest {

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

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
        registry.add("app.baseUrl", () -> "http://localhost");
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        java.util.Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    void post_urls_with_valid_original_url_returns_200_and_short_code() throws Exception {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/very-long-url-for-testing");

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.shortUrl").isNotEmpty())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/very-long-url-for-testing"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void post_urls_with_invalid_url_format_returns_400() throws Exception {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("not-a-url");

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_urls_with_private_ip_returns_400() throws Exception {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("http://192.168.1.1");

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_urls_with_reserved_alias_returns_400() throws Exception {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/url");
        request.setCustomAlias("api");

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_urls_with_duplicate_alias_returns_400() throws Exception {
        CreateLinkRequest request1 = new CreateLinkRequest();
        request1.setOriginalUrl("https://example.com/first");
        request1.setCustomAlias("unique-alias-123");

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        CreateLinkRequest request2 = new CreateLinkRequest();
        request2.setOriginalUrl("https://example.com/second");
        request2.setCustomAlias("unique-alias-123");

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict());
    }

    @Test
    void post_urls_with_custom_alias_returns_custom_alias_in_response() throws Exception {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/custom-test");
        request.setCustomAlias("my-custom-url");

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("my-custom-url"));
    }

    @Test
    void post_urls_with_expires_in_days_returns_expires_at_not_null() throws Exception {
        CreateLinkRequest request = new CreateLinkRequest();
        request.setOriginalUrl("https://example.com/expiring-url");
        request.setExpiresInDays(30);

        mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void get_shortcode_redirects_to_original_url_with_302_found() throws Exception {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://redirect-test.example.com");

        String response = mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shortCode = objectMapper.readTree(response).get("shortCode").asText();

        mockMvc.perform(get("/urls/" + shortCode))
                .andExpect(status().isFound())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", "https://redirect-test.example.com"));
    }

    @Test
    void get_nonexistent_shortcode_returns_404() throws Exception {
        mockMvc.perform(get("/urls/nonexistentcode123"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_expired_shortcode_returns_410_gone() throws Exception {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://will-expire.example.com");
        createRequest.setExpiresInDays(0);

        String response = mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shortCode = objectMapper.readTree(response).get("shortCode").asText();

        Thread.sleep(100);

        mockMvc.perform(get("/urls/" + shortCode))
                .andExpect(status().isGone());
    }

    @Test
    void get_analytics_with_valid_shortcode_returns_200_and_correct_data() throws Exception {
        CreateLinkRequest createRequest = new CreateLinkRequest();
        createRequest.setOriginalUrl("https://analytics-test.example.com");

        String response = mockMvc.perform(post("/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shortCode = objectMapper.readTree(response).get("shortCode").asText();

        mockMvc.perform(get("/urls/" + shortCode))
                .andExpect(status().isFound());

        mockMvc.perform(get("/urls/" + shortCode))
                .andExpect(status().isFound());

        mockMvc.perform(get("/urls/" + shortCode + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(shortCode))
                .andExpect(jsonPath("$.originalUrl").value("https://analytics-test.example.com"))
                .andExpect(jsonPath("$.totalClicks").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void get_analytics_with_nonexistent_shortcode_returns_404() throws Exception {
        mockMvc.perform(get("/urls/nonexistentanalytics/analytics"))
                .andExpect(status().isNotFound());
    }
}