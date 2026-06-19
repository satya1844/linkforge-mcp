package com.satya.urlshortener.service;

import com.satya.urlshortener.Exception.InvalidAliasException;
import com.satya.urlshortener.Exception.InvalidUrlException;
import com.satya.urlshortener.Service.UrlValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidationServiceTest {

    private UrlValidationService urlValidationService;

    @BeforeEach
    void setUp() {
        urlValidationService = new UrlValidationService();
    }

    @Test
    void validateOriginalUrl_valid_url_does_not_throw_exception() {
        assertThatCode(() -> urlValidationService.validateOriginalUrl("https://example.com/some/path?param=value"))
                .doesNotThrowAnyException();
        assertThatCode(() -> urlValidationService.validateOriginalUrl("http://google.com"))
                .doesNotThrowAnyException();
        assertThatCode(() -> urlValidationService.validateOriginalUrl("http://172.217.1.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateOriginalUrl_malformed_url_throws_InvalidUrlException() {
        assertThatThrownBy(() -> urlValidationService.validateOriginalUrl("http://google.com/some path"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("Malformed URL");
    }

    @Test
    void validateOriginalUrl_null_host_throws_InvalidUrlException() {
        assertThatThrownBy(() -> urlValidationService.validateOriginalUrl("mailto:test@example.com"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("URL has no valid host");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost",
            "http://localhost:8080",
            "https://127.0.0.1",
            "http://0.0.0.0",
            "http://169.254.169.254",
            "http://192.168.1.1",
            "https://10.0.0.1",
            "http://172.16.0.1"
    })
    void validateOriginalUrl_private_or_blocked_address_throws_InvalidUrlException(String url) {
        assertThatThrownBy(() -> urlValidationService.validateOriginalUrl(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("URL points to a private or reserved address");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "api", "admin", "health", "swagger", "actuator",
            "login", "logout", "register", "static", "assets",
            "API", "ADMIN"
    })
    void validateCustomAlias_reserved_alias_throws_InvalidAliasException(String alias) {
        assertThatThrownBy(() -> urlValidationService.validateCustomAlias(alias))
                .isInstanceOf(InvalidAliasException.class)
                .hasMessageContaining("is a reserved word");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "my-link", "custom-alias", "super-short-code", "coolurl"
    })
    void validateCustomAlias_valid_alias_does_not_throw_exception(String alias) {
        assertThatCode(() -> urlValidationService.validateCustomAlias(alias))
                .doesNotThrowAnyException();
    }
}
