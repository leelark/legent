package com.legent.tracking.service;

import com.legent.cache.service.CacheService;
import com.legent.common.tracking.TrackingSignatureSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import java.util.Optional;

/**
 * Service for verifying HMAC-SHA256 signatures on tracking URLs.
 * Must use the same signing keys as the delivery-service's TrackingUrlSigner.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingUrlVerifier {

    private final CacheService cacheService;

    @Value("${legent.tracking.signing-key:}")
    private String globalSigningKey;

    @Value("${legent.tracking.key-version:1}")
    private int currentKeyVersion;

    // Cache key prefix must match TrackingUrlSigner in delivery-service
    private static final String SIGNING_KEY_PREFIX = "tracking:signing-key:";

    @PostConstruct
    void validateConfiguration() {
        if (globalSigningKey == null || globalSigningKey.isBlank()) {
            throw new IllegalStateException("Required configuration 'legent.tracking.signing-key' is not set");
        }
        if (globalSigningKey.length() < 32) {
            throw new IllegalStateException("legent.tracking.signing-key must be at least 32 characters for security");
        }
    }

    /**
     * Verifies an HMAC-SHA256 signature for tracking URL parameters.
     *
     * @param signature    The provided signature (Base64 URL-safe encoded)
     * @param tenantId     The tenant ID
     * @param campaignId   The campaign ID
     * @param subscriberId The subscriber ID
     * @param messageId    The message ID
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature(String signature, String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId) {
        if (signature == null || signature.isBlank()) {
            log.warn("Empty signature provided for tracking event");
            return false;
        }

        try {
            String signingKey = getSigningKey(tenantId);
            if (signingKey == null) {
                log.warn("No signing key found for tenant: {}", tenantId);
                return false;
            }

            return TrackingSignatureSupport.verify(
                    signature, signingKey, tenantId, campaignId, subscriberId, messageId, workspaceId, null);
        } catch (Exception e) {
            log.error("Failed to verify tracking URL signature", e);
            return false;
        }
    }

    public boolean verifyClickSignature(String signature, String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url) {
        if (signature == null || signature.isBlank()) {
            log.warn("Empty signature provided for tracking click event");
            return false;
        }

        try {
            String signingKey = getSigningKey(tenantId);
            if (signingKey == null) {
                log.warn("No signing key found for tenant: {}", tenantId);
                return false;
            }

            return TrackingSignatureSupport.verify(
                    signature, signingKey, tenantId, campaignId, subscriberId, messageId, workspaceId, url);
        } catch (Exception e) {
            log.error("Failed to verify tracking click URL signature", e);
            return false;
        }
    }

    /**
     * Retrieves the signing key from Redis (set by delivery-service's TrackingUrlSigner).
     */
    private String getSigningKey(String tenantId) {
        String cacheKey = SIGNING_KEY_PREFIX + currentKeyVersion + ":" + tenantId;
        Optional<String> cachedKey = cacheService.get(cacheKey, String.class);
        return cachedKey.orElseGet(() -> generateSigningKey(tenantId));
    }

    private String generateSigningKey(String tenantId) {
        return TrackingSignatureSupport.deriveTenantSigningKey(globalSigningKey, tenantId, currentKeyVersion);
    }
}
