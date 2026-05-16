package com.legent.campaign.client;

public class ReadinessDependencyException extends RuntimeException {

    public ReadinessDependencyException(String message) {
        super(message);
    }

    public ReadinessDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
