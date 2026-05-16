package com.legent.delivery.service;

import com.legent.cache.service.CacheService;
import com.legent.common.tracking.TrackingSignatureSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Optional;

/**
 * Service for signing and verifying tracking URLs with HMAC-SHA256.
 * Prevents URL parameter tampering and event forgery.
 * Supports key rotation with versioned keys and TTL-based caching.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingUrlSigner {

    private final CacheService cacheService;

    @Value("${legent.tracking.signing-key:}")
    private String globalSigningKey;

    @Value("${legent.tracking.key-ttl-hours:168}") // 7 days default
    private int keyTtlHours;

    @Value("${legent.tracking.key-version:1}")
    private int currentKeyVersion;

    // Cache key prefix for tenant-specific signing keys
    private static final String SIGNING_KEY_PREFIX = "tracking:signing-key:";
    private static final String KEY_VERSION_PREFIX = "tracking:key-version:";
    private static final Duration KEY_OVERLAP_WINDOW = Duration.ofHours(24); // Accept old keys for 24h after rotation

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
     * Generates an HMAC-SHA256 signature for tracking URL parameters.
     *
     * @param tenantId     The tenant ID
     * @param campaignId   The campaign ID
     * @param subscriberId The subscriber ID
     * @param messageId    The message ID
     * @return Base64-encoded HMAC signature
     */
    public String generateSignature(String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId) {
        try {
            String signingKey = getOrCreateSigningKey(tenantId);
            return TrackingSignatureSupport.sign(signingKey, tenantId, campaignId, subscriberId, messageId, workspaceId, null);
        } catch (Exception e) {
            log.error("Failed to generate tracking URL signature", e);
            throw new RuntimeException("Failed to sign tracking URL", e);
        }
    }

    public String generateClickSignature(String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url) {
        try {
            String signingKey = getOrCreateSigningKey(tenantId);
            return TrackingSignatureSupport.sign(signingKey, tenantId, campaignId, subscriberId, messageId, workspaceId, url);
        } catch (Exception e) {
            log.error("Failed to generate tracking click URL signature", e);
            throw new RuntimeException("Failed to sign tracking click URL", e);
        }
    }

    /**
     * Verifies an HMAC-SHA256 signature for tracking URL parameters.
     * Supports key rotation by checking both current and previous key versions.
     *
     * @param signature    The provided signature
     * @param tenantId     The tenant ID
     * @param campaignId   The campaign ID
     * @param subscriberId The subscriber ID
     * @param messageId    The message ID
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature(String signature, String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId) {
        if (signature == null || signature.isBlank()) {
            return false;
        }

        // Try current key first
        try {
            String signingKey = getOrCreateSigningKey(tenantId);
            if (verifyWithKey(signature, tenantId, campaignId, subscriberId, messageId, workspaceId, null, signingKey)) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Failed to verify with current key", e);
        }

        // Try previous key version if within overlap window
        try {
            String previousKey = getPreviousKeyIfValid(tenantId);
            if (previousKey != null && verifyWithKey(signature, tenantId, campaignId, subscriberId, messageId, workspaceId, null, previousKey)) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Failed to verify with previous key", e);
        }

        return false;
    }

    public boolean verifyClickSignature(String signature, String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url) {
        if (signature == null || signature.isBlank()) {
            return false;
        }

        // Try current key first
        try {
            String signingKey = getOrCreateSigningKey(tenantId);
            if (verifyWithKey(signature, tenantId, campaignId, subscriberId, messageId, workspaceId, url, signingKey)) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Failed to verify click with current key", e);
        }

        // Try previous key version if within overlap window
        try {
            String previousKey = getPreviousKeyIfValid(tenantId);
            if (previousKey != null && verifyWithKey(signature, tenantId, campaignId, subscriberId, messageId, workspaceId, url, previousKey)) {
                return true;
            }
        } catch (Exception e) {
            log.debug("Failed to verify click with previous key", e);
        }

        return false;
    }

    private boolean verifyWithKey(String signature, String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url, String signingKey) throws Exception {
        return TrackingSignatureSupport.verify(
                signature, signingKey, tenantId, campaignId, subscriberId, messageId, workspaceId, url);
    }

    /**
     * Gets or creates a signing key for the tenant.
     * Keys are stored in Redis with TTL and support rotation.
     */
    private String getOrCreateSigningKey(String tenantId) {
        String cacheKey = SIGNING_KEY_PREFIX + currentKeyVersion + ":" + tenantId;
        String versionKey = KEY_VERSION_PREFIX + tenantId;

        // Check if we have a cached key
        Optional<String> cachedKey = cacheService.get(cacheKey, String.class);
        if (cachedKey.isPresent()) {
            return cachedKey.get();
        }

        // Generate new key with version
        String newKey = generateSigningKey(tenantId, currentKeyVersion);
        
        // Store previous key info for rotation overlap
        Optional<String> oldVersion = cacheService.get(versionKey, String.class);
        if (oldVersion.isPresent() && !oldVersion.get().equals(String.valueOf(currentKeyVersion))) {
            // Store previous key with shorter TTL for overlap window
            String oldCacheKey = SIGNING_KEY_PREFIX + oldVersion.get() + ":" + tenantId;
            Optional<String> oldKey = cacheService.get(oldCacheKey, String.class);
            if (oldKey.isPresent()) {
                String prevKey = SIGNING_KEY_PREFIX + "prev:" + tenantId;
                cacheService.set(prevKey, oldKey.get(), KEY_OVERLAP_WINDOW);
            }
        }
        
        // Store new key with TTL
        Duration ttl = Duration.ofHours(keyTtlHours);
        cacheService.set(cacheKey, newKey, ttl);
        cacheService.set(versionKey, String.valueOf(currentKeyVersion), ttl);
        
        log.debug("Generated new signing key for tenant {} version {}", tenantId, currentKeyVersion);
        return newKey;
    }

    /**
     * Gets previous key if within the rotation overlap window.
     */
    private String getPreviousKeyIfValid(String tenantId) {
        String prevKey = SIGNING_KEY_PREFIX + "prev:" + tenantId;
        Optional<String> previousKey = cacheService.get(prevKey, String.class);
        return previousKey.orElse(null);
    }

    /**
     * Generates a cryptographically secure signing key for a tenant with versioning.
     */
    private String generateSigningKey(String tenantId, int version) {
        return TrackingSignatureSupport.deriveTenantSigningKey(globalSigningKey, tenantId, version);
    }
}
