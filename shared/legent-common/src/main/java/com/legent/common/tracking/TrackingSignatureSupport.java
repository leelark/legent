package com.legent.common.tracking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared tracking URL signature contract used by delivery and tracking services.
 */
public final class TrackingSignatureSupport {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private TrackingSignatureSupport() {
    }

    public static String deriveTenantSigningKey(String globalSigningKey, String tenantId, int version) {
        try {
            String seed = globalSigningKey + ":" + normalize(tenantId) + ":" + version;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive tracking signing key", e);
        }
    }

    public static String sign(String signingKey,
                              String tenantId,
                              String campaignId,
                              String subscriberId,
                              String messageId,
                              String workspaceId,
                              String url) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] signatureBytes = mac.doFinal(payload(tenantId, campaignId, subscriberId, messageId, workspaceId, url)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign tracking URL", e);
        }
    }

    public static boolean verify(String signature,
                                 String signingKey,
                                 String tenantId,
                                 String campaignId,
                                 String subscriberId,
                                 String messageId,
                                 String workspaceId,
                                 String url) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = sign(signingKey, tenantId, campaignId, subscriberId, messageId, workspaceId, url);
        return constantTimeEquals(signature, expected);
    }

    public static String payload(String tenantId,
                                 String campaignId,
                                 String subscriberId,
                                 String messageId,
                                 String workspaceId,
                                 String url) {
        return String.join(":",
                normalize(tenantId),
                normalize(campaignId),
                normalize(subscriberId),
                normalize(messageId),
                normalize(workspaceId),
                normalize(url));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean constantTimeEquals(String a, String b) {
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
