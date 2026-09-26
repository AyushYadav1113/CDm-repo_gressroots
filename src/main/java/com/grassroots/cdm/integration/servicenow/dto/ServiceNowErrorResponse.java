package com.grassroots.cdm.integration.servicenow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing an error payload returned by ServiceNow Table API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceNowErrorResponse {

    @JsonProperty("error")
    private ErrorDetail error;

    @JsonProperty("status")
    private String status;

    public ServiceNowErrorResponse() {
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFormattedMessage() {
        if (error != null) {
            String msg = error.getMessage() != null ? error.getMessage() : "Unknown ServiceNow error";
            if (error.getDetail() != null && !error.getDetail().isBlank()) {
                return msg + " - " + error.getDetail();
            }
            return msg;
        }
        return "Unknown ServiceNow error";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorDetail {
        @JsonProperty("message")
        private String message;

        @JsonProperty("detail")
        private String detail;

        public ErrorDetail() {
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }
}
