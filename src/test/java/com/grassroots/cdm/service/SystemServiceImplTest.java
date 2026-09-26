package com.grassroots.cdm.service;

import com.grassroots.cdm.dto.SystemStatusDto;
import com.grassroots.cdm.service.impl.SystemServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemServiceImplTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    private SystemServiceImpl systemService;

    @BeforeEach
    void setUp() {
        systemService = new SystemServiceImpl(dataSource, "cdm-core");
    }

    @Test
    @DisplayName("getSystemStatus returns HEALTHY status when DB connection succeeds")
    void getSystemStatus_WhenDbConnected_ReturnsHealthy() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metaData.getDatabaseProductVersion()).thenReturn("16.2");

        SystemStatusDto status = systemService.getSystemStatus();

        assertThat(status).isNotNull();
        assertThat(status.applicationName()).isEqualTo("cdm-core");
        assertThat(status.version()).isEqualTo("1.0.0");
        assertThat(status.status()).isEqualTo("HEALTHY");
        assertThat(status.components()).containsEntry("database", "UP (PostgreSQL 16.2)");
        assertThat(status.components()).containsEntry("orchestration", "READY");
    }

    @Test
    @DisplayName("getSystemStatus returns DEGRADED status when DB connection fails")
    void getSystemStatus_WhenDbFails_ReturnsDegraded() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        SystemStatusDto status = systemService.getSystemStatus();

        assertThat(status).isNotNull();
        assertThat(status.status()).isEqualTo("DEGRADED");
        assertThat(status.components().get("database")).startsWith("DOWN");
    }

    @Test
    @DisplayName("ping returns pong")
    void ping_ReturnsPong() {
        assertThat(systemService.ping()).isEqualTo("pong");
    }
}
