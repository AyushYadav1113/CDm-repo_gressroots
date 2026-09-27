package com.grassroots.cdm.integration.sectigo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Standard error response schema returned by Sectigo SCM REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SectigoErrorResponse {

    @JsonAlias({"status", "errorCode", "error"})
    private String code;

    @JsonAlias({"message", "error_description", "detail"})
    private String description;

    public SectigoErrorResponse() {
    }

    public SectigoErrorResponse(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFormattedMessage() {
        if (description != null && !description.isBlank()) {
            return (code != null) ? String.format("[%s] %s", code, description) : description;
        }
        return code != null ? "Sectigo error code: " + code : "Unknown Sectigo error";
    }
}
