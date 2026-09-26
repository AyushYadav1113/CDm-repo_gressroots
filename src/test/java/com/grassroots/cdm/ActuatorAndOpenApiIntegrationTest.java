package com.grassroots.cdm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorAndOpenApiIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Actuator health endpoint /actuator/health is accessible and returns UP")
    void actuatorHealthEndpointIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("OpenAPI specification endpoint /v3/api-docs is accessible and valid JSON")
    void openApiDocsEndpointIsAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Certificate Deployment Manager (CDM) API");
        assertThat(response.getBody()).contains("openapi");

        // Export generated OpenAPI 3.0 spec for Postman import
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("postman"));
            java.nio.file.Files.writeString(java.nio.file.Path.of("postman/cdm-openapi.json"), response.getBody());
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Swagger UI HTML endpoint /swagger-ui/index.html is accessible")
    void swaggerUiIsAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("swagger-ui");
    }
}
