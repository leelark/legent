package com.legent.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a user attempts to access a resource they don't own or aren't authorized for.
 * Mapped to HTTP 403.
 */
public class UnauthorizedException extends LegentException {

    private static final String ERROR_CODE = "UNAUTHORIZED_ACCESS";

    public UnauthorizedException(String message) {
        super(ERROR_CODE, message, HttpStatus.FORBIDDEN);
    }
}
