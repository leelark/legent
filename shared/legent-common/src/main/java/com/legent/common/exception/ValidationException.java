package com.legent.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when request validation fails beyond Jakarta validation.
 * Mapped to HTTP 422.
 */
public class ValidationException extends LegentException {

    private static final String ERROR_CODE = "VALIDATION_FAILED";

    public ValidationException(String message) {
        super(ERROR_CODE, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public ValidationException(String field, String reason) {
        super(
            ERROR_CODE,
            String.format("Validation failed for field '%s': %s", field, reason),
            HttpStatus.UNPROCESSABLE_ENTITY
        );
    }
}
