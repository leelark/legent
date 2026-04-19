package com.legent.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for the Legent platform.
 * All domain-specific exceptions extend this.
 */
@Getter
public class LegentException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String details;

    public LegentException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = null;
    }

    public LegentException(String errorCode, String message, String details, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public LegentException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = cause.getMessage();
    }
}
