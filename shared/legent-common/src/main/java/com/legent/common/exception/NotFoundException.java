package com.legent.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested resource is not found.
 * Mapped to HTTP 404.
 */
public class NotFoundException extends LegentException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public NotFoundException(String resourceType, String resourceId) {
        super(
            ERROR_CODE,
            String.format("%s with id '%s' not found", resourceType, resourceId),
            HttpStatus.NOT_FOUND
        );
    }

    public NotFoundException(String message) {
        super(ERROR_CODE, message, HttpStatus.NOT_FOUND);
    }
}
