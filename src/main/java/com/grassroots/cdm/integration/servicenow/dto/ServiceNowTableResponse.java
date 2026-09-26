package com.grassroots.cdm.integration.servicenow.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Standard ServiceNow Table API envelope wrapping query results.
 *
 * @param <T> Payload type (e.g. List<ServiceNowCertificateDto> or single object)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceNowTableResponse<T> {

    @JsonProperty("result")
    private T result;

    public ServiceNowTableResponse() {
    }

    public ServiceNowTableResponse(T result) {
        this.result = result;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    public List<ServiceNowCertificateDto> asCertificateList() {
        if (result == null) {
            return Collections.emptyList();
        }
        if (result instanceof List<?> list) {
            return (List<ServiceNowCertificateDto>) list;
        }
        if (result instanceof ServiceNowCertificateDto single) {
            return List.of(single);
        }
        return Collections.emptyList();
    }
}
