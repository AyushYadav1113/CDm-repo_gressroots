package com.grassroots.cdm.integration.sectigo.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Envelope wrapper for paginated Sectigo API responses.
 *
 * @param <T> Payload element type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SectigoPageResponse<T> {

    @JsonAlias({"totalCount", "count", "totalElements"})
    private Integer total;

    @JsonAlias({"items", "results", "data"})
    private List<T> certificates = new ArrayList<>();

    public SectigoPageResponse() {
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public List<T> getCertificates() {
        return certificates;
    }

    public void setCertificates(List<T> certificates) {
        this.certificates = certificates != null ? certificates : new ArrayList<>();
    }
}
