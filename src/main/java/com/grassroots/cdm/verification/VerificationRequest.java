package com.grassroots.cdm.verification;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates parameters and expected criteria for verifying a live TLS endpoint.
 */
public class VerificationRequest {

    private String host;
    private int port = 443;
    private String expectedThumbprintSha256;
    private String expectedThumbprintSha1;
    private String expectedSerialNumber;
    private String expectedCommonName;
    private List<String> expectedSans = new ArrayList<>();
    private String expectedIssuer;
    private boolean checkValidityDates = true;
    private boolean checkHostname = true;
    private boolean allowWildcard = true;
    private int timeoutMs = 5000;

    public VerificationRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getExpectedThumbprintSha256() {
        return expectedThumbprintSha256;
    }

    public void setExpectedThumbprintSha256(String expectedThumbprintSha256) {
        this.expectedThumbprintSha256 = expectedThumbprintSha256;
    }

    public String getExpectedThumbprintSha1() {
        return expectedThumbprintSha1;
    }

    public void setExpectedThumbprintSha1(String expectedThumbprintSha1) {
        this.expectedThumbprintSha1 = expectedThumbprintSha1;
    }

    public String getExpectedSerialNumber() {
        return expectedSerialNumber;
    }

    public void setExpectedSerialNumber(String expectedSerialNumber) {
        this.expectedSerialNumber = expectedSerialNumber;
    }

    public String getExpectedCommonName() {
        return expectedCommonName;
    }

    public void setExpectedCommonName(String expectedCommonName) {
        this.expectedCommonName = expectedCommonName;
    }

    public List<String> getExpectedSans() {
        return expectedSans;
    }

    public void setExpectedSans(List<String> expectedSans) {
        this.expectedSans = expectedSans != null ? expectedSans : new ArrayList<>();
    }

    public String getExpectedIssuer() {
        return expectedIssuer;
    }

    public void setExpectedIssuer(String expectedIssuer) {
        this.expectedIssuer = expectedIssuer;
    }

    public boolean isCheckValidityDates() {
        return checkValidityDates;
    }

    public void setCheckValidityDates(boolean checkValidityDates) {
        this.checkValidityDates = checkValidityDates;
    }

    public boolean isCheckHostname() {
        return checkHostname;
    }

    public void setCheckHostname(boolean checkHostname) {
        this.checkHostname = checkHostname;
    }

    public boolean isAllowWildcard() {
        return allowWildcard;
    }

    public void setAllowWildcard(boolean allowWildcard) {
        this.allowWildcard = allowWildcard;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public static class Builder {
        private final VerificationRequest request = new VerificationRequest();

        public Builder host(String host) {
            request.setHost(host);
            return this;
        }

        public Builder port(int port) {
            request.setPort(port);
            return this;
        }

        public Builder expectedThumbprintSha256(String thumbprint) {
            request.setExpectedThumbprintSha256(thumbprint);
            return this;
        }

        public Builder expectedThumbprintSha1(String thumbprint) {
            request.setExpectedThumbprintSha1(thumbprint);
            return this;
        }

        public Builder expectedSerialNumber(String serial) {
            request.setExpectedSerialNumber(serial);
            return this;
        }

        public Builder expectedCommonName(String commonName) {
            request.setExpectedCommonName(commonName);
            return this;
        }

        public Builder expectedSans(List<String> sans) {
            request.setExpectedSans(sans);
            return this;
        }

        public Builder expectedIssuer(String issuer) {
            request.setExpectedIssuer(issuer);
            return this;
        }

        public Builder checkValidityDates(boolean checkValidityDates) {
            request.setCheckValidityDates(checkValidityDates);
            return this;
        }

        public Builder checkHostname(boolean checkHostname) {
            request.setCheckHostname(checkHostname);
            return this;
        }

        public Builder allowWildcard(boolean allowWildcard) {
            request.setAllowWildcard(allowWildcard);
            return this;
        }

        public Builder timeoutMs(int timeoutMs) {
            request.setTimeoutMs(timeoutMs);
            return this;
        }

        public VerificationRequest build() {
            return request;
        }
    }
}
