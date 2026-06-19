package com.satya.urlshortener.exception;

import com.satya.urlshortener.Dto.ErrorResponse;
import com.satya.urlshortener.Exception.AliasAlreadyInUseException;
import com.satya.urlshortener.Exception.GlobalExceptionHandler;
import com.satya.urlshortener.Exception.InvalidAliasException;
import com.satya.urlshortener.Exception.InvalidUrlException;
import com.satya.urlshortener.Exception.ShortCodeExpiredException;
import com.satya.urlshortener.Exception.ShortCodeNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/test-uri");
    }

    @Test
    void handleAliasAlreadyInUse_returns_409_conflict() {
        // Arrange
        AliasAlreadyInUseException ex = new AliasAlreadyInUseException("Alias in use");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAliasAlreadyInUse(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Conflict");
        assertThat(response.getBody().getMessage()).isEqualTo("Alias in use");
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getPath()).isEqualTo("/test-uri");
    }

    @Test
    void handleInvalidAlias_returns_400_bad_request() {
        // Arrange
        InvalidAliasException ex = new InvalidAliasException("Invalid alias");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidAlias(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid alias");
    }

    @Test
    void handleInvalidUrl_returns_400_bad_request() {
        // Arrange
        InvalidUrlException ex = new InvalidUrlException("Invalid URL");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidUrl(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid URL");
    }

    @Test
    void handleShortCodeNotFound_returns_404_not_found() {
        // Arrange
        ShortCodeNotFoundException ex = new ShortCodeNotFoundException("notfound");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleShortCodeNotFound(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getMessage()).isEqualTo("Short code 'notfound' not found.");
    }

    @Test
    void handleShortCodeExpired_returns_410_gone() {
        // Arrange
        ShortCodeExpiredException ex = new ShortCodeExpiredException("expiredCode");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleShortCodeExpired(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody().getError()).isEqualTo("Gone");
        assertThat(response.getBody().getMessage()).isEqualTo("Short code 'expiredCode' has expired.");
    }

    @Test
    void handleGenericException_returns_500_internal_server_error() {
        // Arrange
        Exception ex = new Exception("Generic error");

        // Act
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex, request);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong");
    }
}
