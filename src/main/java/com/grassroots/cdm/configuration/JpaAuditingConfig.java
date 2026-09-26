package com.grassroots.cdm.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Dedicated configuration enabling Spring Data JPA auditing.
 * Separated from @SpringBootApplication to avoid breaking slicing tests such as @WebMvcTest.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
