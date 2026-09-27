package com.grassroots.cdm.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * HealthIndicator verifying PostgreSQL database connectivity without leaking connection credentials.
 */
@Component
public class CdmDatabaseHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public CdmDatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        if (dataSource == null) {
            return Health.down().withDetail("database", "PostgreSQL").withDetail("status", "DATA_SOURCE_NOT_CONFIGURED").build();
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(3);
            stmt.execute("SELECT 1");
            return Health.up()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "CONNECTED")
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "UNREACHABLE")
                    .withDetail("error", "Database connection validation failed")
                    .build();
        }
    }
}
