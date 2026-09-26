package com.grassroots.cdm.exception;

import com.grassroots.cdm.dto.ValidationError;

import java.util.List;

/**
 * Thrown when business validation fails.
 */
public class ValidationException extends CdmException {

    private final List<ValidationError> validationErrors;

    public ValidationException(String message) {
        super(message);
        this.validationErrors = List.of();
    }

    public ValidationException(String message, List<ValidationError> validationErrors) {
        super(message);
        this.validationErrors = validationErrors != null ? List.copyOf(validationErrors) : List.of();
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }
}
