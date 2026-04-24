package com.legent.tracking.service;

import com.legent.cache.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
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

    // Cache key prefix must match TrackingUrlSigner in delivery-service
    private static final String SIGNING_KEY_PREFIX = "tracking:signing-key:";

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
    public boolean verifySignature(String signature, String tenantId, String campaignId, String subscriberId, String messageId) {
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

            String data = String.format("%s:%s:%s:%s", tenantId, campaignId, subscriberId, messageId);

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

    /**
     * Retrieves the signing key from Redis (set by delivery-service's TrackingUrlSigner).
     */
    private String getSigningKey(String tenantId) {
        String cacheKey = SIGNING_KEY_PREFIX + tenantId;
        Optional<String> cachedKey = cacheService.get(cacheKey, String.class);
        return cachedKey.orElse(null);
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
