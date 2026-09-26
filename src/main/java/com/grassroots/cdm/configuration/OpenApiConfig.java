package com.grassroots.cdm.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 specification configuration for the CDM platform.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BasicAuth";

    @Bean
    public OpenAPI cdmOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Certificate Deployment Manager (CDM) API")
                        .description("Enterprise orchestration platform for discovering, matching, and deploying SSL/TLS certificates across heterogeneous infrastructure via MID Server.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CDM Engineering Operations")
                                .email("cdm-support@grassroots.internal"))
                        .license(new License().name("Internal Enterprise License")))
                .servers(List.of(
                        new Server().url("/").description("Default Server URL")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic")
                                        .description("HTTP Basic Authentication for CDM orchestration and administrative operations")));
    }
}
