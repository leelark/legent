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
import java.util.Base64;
import java.util.Optional;

/**
 * Service for signing and verifying tracking URLs with HMAC-SHA256.
 * Prevents URL parameter tampering and event forgery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingUrlSigner {

    private final CacheService cacheService;

    @Value("${legent.tracking.signing-key:}")
    private String globalSigningKey;

    // Cache key prefix for tenant-specific signing keys
    private static final String SIGNING_KEY_PREFIX = "tracking:signing-key:";

    @PostConstruct
    void validateConfiguration() {
        if (globalSigningKey == null || globalSigningKey.isBlank()) {
            throw new IllegalStateException("Required configuration 'legent.tracking.signing-key' is not set");
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
    public String generateSignature(String tenantId, String campaignId, String subscriberId, String messageId) {
        try {
            String signingKey = getOrCreateSigningKey(tenantId);
            String data = signaturePayload(tenantId, campaignId, subscriberId, messageId, null);

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

    public String generateClickSignature(String tenantId, String campaignId, String subscriberId, String messageId, String url) {
        try {
            String signingKey = getOrCreateSigningKey(tenantId);
            String data = signaturePayload(tenantId, campaignId, subscriberId, messageId, url);

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
     *
     * @param signature    The provided signature
     * @param tenantId     The tenant ID
     * @param campaignId   The campaign ID
     * @param subscriberId The subscriber ID
     * @param messageId    The message ID
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature(String signature, String tenantId, String campaignId, String subscriberId, String messageId) {
        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            String signingKey = getOrCreateSigningKey(tenantId);
            String data = signaturePayload(tenantId, campaignId, subscriberId, messageId, null);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] expectedSignatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSignatureBytes);

            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(signature, expectedSignature);
        } catch (Exception e) {
            log.error("Failed to verify tracking URL signature", e);
            return false;
        }
    }

    public boolean verifyClickSignature(String signature, String tenantId, String campaignId, String subscriberId, String messageId, String url) {
        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            String signingKey = getOrCreateSigningKey(tenantId);
            String data = signaturePayload(tenantId, campaignId, subscriberId, messageId, url);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] expectedSignatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSignatureBytes);

            return constantTimeEquals(signature, expectedSignature);
        } catch (Exception e) {
            log.error("Failed to verify tracking click URL signature", e);
            return false;
        }
    }

    /**
     * Gets or creates a signing key for the tenant.
     * Keys are stored in Redis with TTL and rotated automatically.
     */
    private String getOrCreateSigningKey(String tenantId) {
        String cacheKey = SIGNING_KEY_PREFIX + tenantId;

        Optional<String> cachedKey = cacheService.get(cacheKey, String.class);
        if (cachedKey.isPresent()) {
            return cachedKey.get();
        }

        // Deterministic per-tenant key keeps sent tracking URLs valid across Redis restarts.
        String newKey = generateSigningKey(tenantId);
        cacheService.set(cacheKey, newKey);
        return newKey;
    }

    /**
     * Generates a cryptographically secure signing key for a tenant.
     */
    private String generateSigningKey(String tenantId) {
        try {
            String seed = globalSigningKey + ":" + normalize(tenantId);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signing key", e);
        }
    }

    private String signaturePayload(String tenantId, String campaignId, String subscriberId, String messageId, String url) {
        return String.join(":",
                normalize(tenantId),
                normalize(campaignId),
                normalize(subscriberId),
                normalize(messageId),
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
