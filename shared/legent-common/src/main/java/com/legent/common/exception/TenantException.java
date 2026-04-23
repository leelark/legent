package com.legent.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when tenant context is missing or invalid.
 */
public class TenantException extends LegentException {

    public TenantException(String message) {
        super("MISSING_TENANT", message, HttpStatus.BAD_REQUEST);
    }

    public TenantException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.BAD_REQUEST);
    }
}
