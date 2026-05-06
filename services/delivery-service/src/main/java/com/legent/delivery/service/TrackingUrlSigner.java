package com.legent.delivery.service;

import com.legent.cache.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
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
            String data = signaturePayload(tenantId, campaignId, subscriberId, messageId, workspaceId, null);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Use URL-safe Base64 encoding
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to generate tracking URL signature", e);
            throw new RuntimeException("Failed to sign tracking URL", e);
        }
    }

    public String generateClickSignature(String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url) {
        try {
            String signingKey = getOrCreateSigningKey(tenantId);
            String data = signaturePayload(tenantId, campaignId, subscriberId, messageId, workspaceId, url);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
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
        String data = signaturePayload(tenantId, campaignId, subscriberId, messageId, workspaceId, url);

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] expectedSignatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSignatureBytes);

        // Constant-time comparison to prevent timing attacks
        return constantTimeEquals(signature, expectedSignature);
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
        try {
            String seed = globalSigningKey + ":" + normalize(tenantId) + ":" + version;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signing key", e);
        }
    }

    private String signaturePayload(String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url) {
        return String.join(":",
                normalize(tenantId),
                normalize(campaignId),
                normalize(subscriberId),
                normalize(messageId),
                normalize(workspaceId),
                normalize(url));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
