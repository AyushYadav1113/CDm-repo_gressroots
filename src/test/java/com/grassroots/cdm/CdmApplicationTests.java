package com.grassroots.cdm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CdmApplicationTests extends AbstractIntegrationTest {

    @Test
    @DisplayName("Spring ApplicationContext loads successfully with containerized PostgreSQL and Flyway")
    void contextLoads() {
        assertThat(postgres.isRunning()).isTrue();
    }
}
