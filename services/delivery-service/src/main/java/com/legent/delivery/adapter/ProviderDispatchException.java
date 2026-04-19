package com.legent.delivery.adapter;

public class ProviderDispatchException extends RuntimeException {
    
    private final boolean isPermanent;

    public ProviderDispatchException(String message, boolean isPermanent, Throwable cause) {
        super(message, cause);
        this.isPermanent = isPermanent;
    }
    
    public ProviderDispatchException(String message, boolean isPermanent) {
        super(message);
        this.isPermanent = isPermanent;
    }

    public boolean isPermanent() {
        return isPermanent;
    }
}
