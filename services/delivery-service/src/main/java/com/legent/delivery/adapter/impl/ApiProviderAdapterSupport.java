package com.legent.delivery.adapter.impl;

import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.service.CredentialEncryptionService;

import java.net.URI;
import java.util.Map;

abstract class ApiProviderAdapterSupport {

    protected final CredentialEncryptionService credentialEncryptionService;

    protected ApiProviderAdapterSupport(CredentialEncryptionService credentialEncryptionService) {
        this.credentialEncryptionService = credentialEncryptionService;
    }

    protected String resolveSecret(SmtpProvider config) {
        if (config.getEncryptedPassword() != null && !config.getEncryptedPassword().isBlank()
                && config.getEncryptionIv() != null && !config.getEncryptionIv().isBlank()) {
            return credentialEncryptionService.decrypt(config.getEncryptedPassword(), config.getEncryptionIv());
        }
        if (config.getUsername() != null && !config.getUsername().isBlank()) {
            return config.getUsername();
        }
        throw new IllegalStateException("No API credential configured for provider " + config.getId());
    }

    protected URI resolveBaseUri(SmtpProvider config, String fallbackUrl) {
        String host = config.getHost();
        if (host != null && !host.isBlank()) {
            String normalized = host.startsWith("http://") || host.startsWith("https://") ? host : "https://" + host;
            return URI.create(normalized);
        }
        return URI.create(fallbackUrl);
    }

    protected String resolveFromEmail(Map<String, String> metadata, SmtpProvider config, String recipient) {
        String fromMetadata = metadata != null ? metadata.get("From-Email") : null;
        if (fromMetadata != null && !fromMetadata.isBlank()) {
            return fromMetadata;
        }
        String username = config.getUsername();
        if (username != null && username.contains("@")) {
            return username;
        }
        int at = recipient.indexOf('@');
        if (at > 0 && at < recipient.length() - 1) {
            return "no-reply@" + recipient.substring(at + 1);
        }
        return "no-reply@example.com";
    }

    protected boolean isPermanentByStatus(int statusCode) {
        return statusCode >= 400 && statusCode < 500 && statusCode != 408 && statusCode != 429;
    }

    protected ProviderDispatchException dispatchFailure(String message, int statusCode, Throwable cause) {
        boolean permanent = isPermanentByStatus(statusCode);
        return new ProviderDispatchException(message, permanent, cause);
    }
}

