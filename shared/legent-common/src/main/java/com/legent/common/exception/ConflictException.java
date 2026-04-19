package com.legent.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a resource conflict occurs (e.g., duplicate key).
 * Mapped to HTTP 409.
 */
public class ConflictException extends LegentException {

    private static final String ERROR_CODE = "RESOURCE_CONFLICT";

    public ConflictException(String message) {
        super(ERROR_CODE, message, HttpStatus.CONFLICT);
    }

    public ConflictException(String resourceType, String field, String value) {
        super(
            ERROR_CODE,
            String.format("%s with %s '%s' already exists", resourceType, field, value),
            HttpStatus.CONFLICT
        );
    }
}
