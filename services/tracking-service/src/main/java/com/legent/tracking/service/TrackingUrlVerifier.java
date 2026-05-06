package com.legent.tracking.service;

import com.legent.cache.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
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

    // Cache key prefix must match TrackingUrlSigner in delivery-service
    private static final String SIGNING_KEY_PREFIX = "tracking:signing-key:";

    @PostConstruct
    void validateConfiguration() {
        if (globalSigningKey == null || globalSigningKey.isBlank()) {
            throw new IllegalStateException("Required configuration 'legent.tracking.signing-key' is not set");
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

            String data = signaturePayload(tenantId, campaignId, subscriberId, messageId, workspaceId, null);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] expectedSignatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSignatureBytes);

            // Constant-time comparison to prevent timing attacks
            if (constantTimeEquals(signature, expectedSignature)) {
                return true;
            }
            // Legacy compatibility for previously-signed URLs without workspace.
            String legacyData = signaturePayload(tenantId, campaignId, subscriberId, messageId, null, null);
            byte[] legacySignatureBytes = mac.doFinal(legacyData.getBytes(StandardCharsets.UTF_8));
            String legacySignature = Base64.getUrlEncoder().withoutPadding().encodeToString(legacySignatureBytes);
            return constantTimeEquals(signature, legacySignature);
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

            String data = signaturePayload(tenantId, campaignId, subscriberId, messageId, workspaceId, url);

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] expectedSignatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSignatureBytes);

            if (constantTimeEquals(signature, expectedSignature)) {
                return true;
            }
            // Legacy compatibility for previously-signed URLs without workspace.
            String legacyData = signaturePayload(tenantId, campaignId, subscriberId, messageId, null, url);
            byte[] legacySignatureBytes = mac.doFinal(legacyData.getBytes(StandardCharsets.UTF_8));
            String legacySignature = Base64.getUrlEncoder().withoutPadding().encodeToString(legacySignatureBytes);
            return constantTimeEquals(signature, legacySignature);
        } catch (Exception e) {
            log.error("Failed to verify tracking click URL signature", e);
            return false;
        }
    }

    /**
     * Retrieves the signing key from Redis (set by delivery-service's TrackingUrlSigner).
     */
    private String getSigningKey(String tenantId) {
        String cacheKey = SIGNING_KEY_PREFIX + tenantId;
        Optional<String> cachedKey = cacheService.get(cacheKey, String.class);
        return cachedKey.orElseGet(() -> generateSigningKey(tenantId));
    }

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
