package com.legent.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class InternalApiTokenValidator {

    private static final int MIN_TOKEN_LENGTH = 32;

    private InternalApiTokenValidator() {
    }

    public static String requireConfigured(String propertyName, String token) {
        if (!isConfiguredSecret(token)) {
            throw new IllegalStateException(propertyName + " must be configured with a non-placeholder secret");
        }
        return token.trim();
    }

    public static boolean matches(String configuredToken, String candidateToken) {
        String configured = requireConfigured("legent.internal.api-token", configuredToken);
        if (candidateToken == null || candidateToken.isBlank()) {
            return false;
        }
        byte[] configuredBytes = configured.getBytes(StandardCharsets.UTF_8);
        byte[] candidateBytes = candidateToken.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(configuredBytes, candidateBytes);
    }

    public static boolean isConfiguredSecret(String token) {
        if (token == null) {
            return false;
        }
        String trimmed = token.trim();
        if (trimmed.length() < MIN_TOKEN_LENGTH) {
            return false;
        }
        if (isPlaceholderLikeSecret(trimmed)) {
            return false;
        }
        if (trimmed.chars().distinct().count() < 8) {
            return false;
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);
        boolean hasDigitOrSymbol = trimmed.chars().anyMatch(ch -> Character.isDigit(ch) || !Character.isLetterOrDigit(ch));
        return hasLetter && hasDigitOrSymbol;
    }

    public static boolean isPlaceholderLikeSecret(String token) {
        if (token == null) {
            return true;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank()
                || normalized.contains("change_me")
                || normalized.contains("changeme")
                || normalized.contains("replace")
                || normalized.contains("placeholder")
                || normalized.contains("example")
                || normalized.contains("dummy")
                || normalized.contains("dev-token")
                || normalized.equals("password")
                || normalized.equals("minioadmin");
    }
}
