package com.grassroots.cdm.health;

import com.grassroots.cdm.credential.cyberark.CyberArkClient;
import com.grassroots.cdm.integration.midserver.MidServerClient;
import com.grassroots.cdm.integration.sectigo.SectigoClient;
import com.grassroots.cdm.integration.servicenow.ServiceNowClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CDM health indicators.
 * Verifies correct status reporting and no credential / topology leakage.
 */
class CdmHealthIndicatorsTest {

    // ─── Database ─────────────────────────────────────────────────────────────

    @Test
    void database_health_returns_UP_when_connection_succeeds() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.execute(anyString())).thenReturn(true);

        CdmDatabaseHealthIndicator indicator = new CdmDatabaseHealthIndicator(dataSource);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("database");
        assertThat(health.getDetails().get("status")).isEqualTo("CONNECTED");
    }

    @Test
    void database_health_returns_DOWN_when_connection_fails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        CdmDatabaseHealthIndicator indicator = new CdmDatabaseHealthIndicator(dataSource);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("status");
        assertThat(health.getDetails().get("status")).isEqualTo("UNREACHABLE");
        // Must not expose connection string or passwords
        assertThat(health.getDetails().toString()).doesNotContain("password");
        assertThat(health.getDetails().toString()).doesNotContain("jdbc:");
    }

    @Test
    void database_health_returns_DOWN_when_datasource_is_null() {
        CdmDatabaseHealthIndicator indicator = new CdmDatabaseHealthIndicator(null);
        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    // ─── ServiceNow ───────────────────────────────────────────────────────────

    @Test
    void servicenow_health_returns_UP_when_probe_succeeds() {
        ServiceNowClient client = mock(ServiceNowClient.class);
        when(client.fetchCertificates(anyString(), anyInt(), anyInt(), anyString())).thenReturn(List.of());

        ServiceNowHealthIndicator indicator = new ServiceNowHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("status")).isEqualTo("REACHABLE");
    }

    @Test
    void servicenow_health_returns_DOWN_when_client_throws() {
        ServiceNowClient client = mock(ServiceNowClient.class);
        when(client.fetchCertificates(anyString(), anyInt(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("ServiceNow 503"));

        ServiceNowHealthIndicator indicator = new ServiceNowHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("status")).isEqualTo("UNAVAILABLE");
        // No internal URL or credentials should be leaked
        assertThat(health.getDetails().toString()).doesNotContain("username");
        assertThat(health.getDetails().toString()).doesNotContain("password");
    }

    @Test
    void servicenow_health_returns_UNKNOWN_when_client_is_null() {
        ServiceNowHealthIndicator indicator = new ServiceNowHealthIndicator(null);
        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
    }

    // ─── Sectigo ──────────────────────────────────────────────────────────────

    @Test
    void sectigo_health_returns_UP_when_probe_succeeds() {
        SectigoClient client = mock(SectigoClient.class);
        when(client.fetchCertificates(anyInt(), anyInt(), anyString(), anyString())).thenReturn(List.of());

        SectigoHealthIndicator indicator = new SectigoHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void sectigo_health_returns_DOWN_when_client_throws() {
        SectigoClient client = mock(SectigoClient.class);
        when(client.fetchCertificates(anyInt(), anyInt(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Sectigo 401"));

        SectigoHealthIndicator indicator = new SectigoHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    // ─── CyberArk ─────────────────────────────────────────────────────────────

    @Test
    void cyberark_health_returns_UP_when_ping_succeeds() {
        CyberArkClient client = mock(CyberArkClient.class);
        when(client.ping()).thenReturn(true);

        CyberArkHealthIndicator indicator = new CyberArkHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("status")).isEqualTo("REACHABLE");
        // AppID must not appear in health output
        assertThat(health.getDetails().toString()).doesNotContain("AppID");
    }

    @Test
    void cyberark_health_returns_DOWN_when_ping_returns_false() {
        CyberArkClient client = mock(CyberArkClient.class);
        when(client.ping()).thenReturn(false);

        CyberArkHealthIndicator indicator = new CyberArkHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void cyberark_health_returns_DOWN_when_client_throws() {
        CyberArkClient client = mock(CyberArkClient.class);
        when(client.ping()).thenThrow(new RuntimeException("CyberArk unreachable"));

        CyberArkHealthIndicator indicator = new CyberArkHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().toString()).doesNotContain("base-url");
    }

    // ─── MID Server ───────────────────────────────────────────────────────────

    @Test
    void midserver_health_returns_UP_when_checkHealth_returns_true() {
        MidServerClient client = mock(MidServerClient.class);
        when(client.checkHealth()).thenReturn(true);

        MidServerHealthIndicator indicator = new MidServerHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void midserver_health_returns_DOWN_when_checkHealth_returns_false() {
        MidServerClient client = mock(MidServerClient.class);
        when(client.checkHealth()).thenReturn(false);

        MidServerHealthIndicator indicator = new MidServerHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void midserver_health_returns_DOWN_and_does_not_leak_hostname_when_client_throws() {
        MidServerClient client = mock(MidServerClient.class);
        when(client.checkHealth()).thenThrow(new RuntimeException("Connection refused mid-server.internal:8443"));

        MidServerHealthIndicator indicator = new MidServerHealthIndicator(client);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        // Internal network host should not appear in the health response body
        assertThat(health.getDetails().get("error")).isEqualTo("MID Server probe failed");
        assertThat(health.getDetails().get("error").toString()).doesNotContain("mid-server.internal");
    }
}
