package com.grassroots.cdm.integration.midserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Cancellation request contract sent to MID Server.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MidServerCancelRequestDto {

    private String reason;
    private boolean forceKill = false;

    public MidServerCancelRequestDto() {
    }

    public MidServerCancelRequestDto(String reason, boolean forceKill) {
        this.reason = reason;
        this.forceKill = forceKill;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isForceKill() {
        return forceKill;
    }

    public void setForceKill(boolean forceKill) {
        this.forceKill = forceKill;
    }
}
