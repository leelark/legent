package com.legent.delivery.adapter;

import java.util.Map;


/**
 * Common abstraction for dispatching emails through multiple backends.
 */
public interface ProviderAdapter {

    String getProviderType();

    /**
     * Dispatch an email.
     * @param to recipient email
     * @param subject email subject
     * @param htmlBody rendered HTML content
     * @param metadata messageID and campaign identifiers for headers
     * @throws ProviderDispatchException if dispatch fails (transient or permanent)
     */
    void sendEmail(String to, String subject, String htmlBody, Map<String, String> metadata) throws ProviderDispatchException;
}
