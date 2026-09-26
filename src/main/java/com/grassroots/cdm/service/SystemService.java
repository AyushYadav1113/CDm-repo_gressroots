package com.grassroots.cdm.service;

import com.grassroots.cdm.dto.SystemStatusDto;

/**
 * Service contract for platform diagnostics, environment inspection, and health validation.
 */
public interface SystemService {

    /**
     * Inspects runtime state, database connectivity, and component health.
     *
     * @return current system diagnostic status
     */
    SystemStatusDto getSystemStatus();

    /**
     * Performs a lightweight ping check.
     *
     * @return ping acknowledgement string
     */
    String ping();
}
