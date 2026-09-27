package com.grassroots.cdm.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Scheduling configuration.
 * Can be disabled in test profiles by setting {@code cdm.orchestration.scheduling-enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cdm.orchestration.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
