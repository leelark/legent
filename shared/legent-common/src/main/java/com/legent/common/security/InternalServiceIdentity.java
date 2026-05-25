package com.legent.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Set;

public final class InternalServiceIdentity {

    public static final String HEADER_SERVICE = "X-Internal-Service";
    public static final String HEADER_SIGNATURE_TIMESTAMP = "X-Internal-Signature-Timestamp";
    public static final String HEADER_SIGNATURE = "X-Internal-Signature";
    public static final String ACTION_AUDIENCE_RESOLUTION_CHUNK_READ = "audience-resolution-chunk.read";
    public static final String ACTION_DELIVERABILITY_SUPPRESSION_LIST_READ = "deliverability-suppression.list";
    public static final String ACTION_DELIVERABILITY_SUPPRESSION_HISTORY_READ = "deliverability-suppression.history";
    public static final String ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK = "deliverability-suppression.bulk-check";

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_VERSION = "v1";
    private static final Duration DEFAULT_MAX_SKEW = Duration.ofMinutes(5);

    private InternalServiceIdentity() {
    }

    public static String sign(
            String configuredCredential,
            String serviceName,
            String tenantId,
            String workspaceId,
            String action,
            Instant timestamp) {
        String signingKey = InternalApiTokenValidator.requireConfigured("legent.internal.api-token", configuredCredential);
        String payload = canonicalPayload(serviceName, tenantId, workspaceId, action, timestamp);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign internal service request", ex);
        }
    }

    public static boolean matches(
            String configuredCredential,
            String presentedCredential,
            String presentedService,
            Set<String> allowedServices,
            String tenantId,
            String workspaceId,
            String action,
            String timestamp,
            String presentedSignature) {
        return matches(
                configuredCredential,
                presentedCredential,
                presentedService,
                allowedServices,
                tenantId,
                workspaceId,
                action,
                timestamp,
                presentedSignature,
                Clock.systemUTC(),
                DEFAULT_MAX_SKEW);
    }

    public static String scopedAction(String action, String... scopes) {
        StringBuilder builder = new StringBuilder(require(action, "action"));
        for (String scope : scopes) {
            builder.append(':').append(require(scope, "scope"));
        }
        return builder.toString();
    }

    public static boolean matches(
            String configuredCredential,
            String presentedCredential,
            String presentedService,
            Set<String> allowedServices,
            String tenantId,
            String workspaceId,
            String action,
            String timestamp,
            String presentedSignature,
            Clock clock,
            Duration maxSkew) {
        if (!InternalApiTokenValidator.matches(configuredCredential, presentedCredential)
                || isBlank(presentedService)
                || allowedServices == null
                || !allowedServices.contains(presentedService.trim())
                || isBlank(timestamp)
                || isBlank(presentedSignature)) {
            return false;
        }

        Instant signedAt;
        try {
            signedAt = Instant.parse(timestamp.trim());
        } catch (DateTimeParseException ex) {
            return false;
        }
        Duration allowedSkew = maxSkew == null || maxSkew.isNegative() || maxSkew.isZero()
                ? DEFAULT_MAX_SKEW
                : maxSkew;
        if (Duration.between(signedAt, clock.instant()).abs().compareTo(allowedSkew) > 0) {
            return false;
        }

        String expectedSignature = sign(
                configuredCredential,
                presentedService,
                tenantId,
                workspaceId,
                action,
                signedAt);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                presentedSignature.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String canonicalPayload(
            String serviceName,
            String tenantId,
            String workspaceId,
            String action,
            Instant timestamp) {
        return String.join("\n",
                SIGNATURE_VERSION,
                require(serviceName, "serviceName"),
                require(tenantId, "tenantId"),
                require(workspaceId, "workspaceId"),
                require(action, "action"),
                requireTimestamp(timestamp).toString());
    }

    private static Instant requireTimestamp(Instant timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
        return timestamp;
    }

    private static String require(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
