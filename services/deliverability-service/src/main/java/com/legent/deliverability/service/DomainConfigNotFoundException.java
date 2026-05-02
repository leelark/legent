package com.legent.deliverability.service;

/**
 * Exception thrown when domain configuration is not found.
 * AUDIT-008: Explicit exception for missing domain config.
 */
public class DomainConfigNotFoundException extends RuntimeException {
    public DomainConfigNotFoundException(String message) {
        super(message);
    }
}
