package com.grassroots.cdm.exception;

import com.grassroots.cdm.dto.ErrorResponse;
import com.grassroots.cdm.dto.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
    }

    @Test
    @DisplayName("handleResourceNotFoundException maps to 404 NOT_FOUND")
    void handleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Certificate", "12345");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFoundException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).contains("Certificate with identifier '12345' was not found");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/test");
    }

    @Test
    @DisplayName("handleValidationException maps to 400 BAD_REQUEST with validation details")
    void handleValidationException() {
        List<ValidationError> errors = List.of(new ValidationError("targetHost", null, "Host cannot be empty"));
        ValidationException ex = new ValidationException("Invalid job payload", errors);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().validationErrors()).hasSize(1);
        assertThat(response.getBody().validationErrors().getFirst().field()).isEqualTo("targetHost");
    }

    @Test
    @DisplayName("handleIntegrationException maps to 502 BAD_GATEWAY")
    void handleIntegrationException() {
        IntegrationException ex = new IntegrationException("ServiceNow", "Connection timed out");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleIntegrationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(502);
        assertThat(response.getBody().message()).isEqualTo("[ServiceNow] Connection timed out");
    }

    @Test
    @DisplayName("handleAccessDeniedException maps to 403 FORBIDDEN")
    void handleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDeniedException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(403);
    }
}
