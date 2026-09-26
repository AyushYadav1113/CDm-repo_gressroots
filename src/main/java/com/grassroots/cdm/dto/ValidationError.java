package com.grassroots.cdm.dto;

/**
 * Encapsulates a validation error for a specific field or constraint.
 */
public record ValidationError(
        String field,
        Object rejectedValue,
        String message
) {}
