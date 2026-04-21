package com.legent.delivery.adapter;

import java.util.Map;
import com.legent.delivery.domain.SmtpProvider;

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
     * @param config provider configuration (host, credentials, etc)
     * @throws ProviderDispatchException if dispatch fails (transient or permanent)
     */
    void sendEmail(@org.springframework.lang.NonNull String to, @org.springframework.lang.NonNull String subject, @org.springframework.lang.NonNull String htmlBody, Map<String, String> metadata, SmtpProvider config) throws ProviderDispatchException;
}
