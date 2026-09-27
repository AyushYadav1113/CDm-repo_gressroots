package com.grassroots.cdm.integration.midserver.exception;

/**
 * Thrown when the MID Server returns an unparseable, malformed, or protocol-invalid response.
 */
public class MidServerInvalidResponseException extends MidServerException {

    private final String rawResponseBody;

    public MidServerInvalidResponseException(String message) {
        super(message);
        this.rawResponseBody = null;
    }

    public MidServerInvalidResponseException(String message, String rawResponseBody) {
        super(message);
        this.rawResponseBody = rawResponseBody;
    }

    public MidServerInvalidResponseException(String message, String rawResponseBody, Throwable cause) {
        super(message, cause);
        this.rawResponseBody = rawResponseBody;
    }

    public String getRawResponseBody() {
        return rawResponseBody;
    }
}
