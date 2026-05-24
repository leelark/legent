package com.legent.automation.service;

public class AutomationObjectStorageException extends RuntimeException {

    public AutomationObjectStorageException(String message) {
        super(message);
    }

    public AutomationObjectStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
