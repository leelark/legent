package com.legent.platform.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookResponseSanitizerTest {

    @Test
    void sanitizeRedactsSensitiveResponseFieldsAndAuthSchemes() {
        String sanitized = WebhookResponseSanitizer.sanitize(
                "{\"token\":\"abc123\",\"client_secret\":\"super-secret\",\"message\":\"Bearer eyJhbGciOiJIUzI1NiJ9\"}");

        assertThat(sanitized)
                .contains("\"token\":\"[redacted]\"")
                .contains("\"client_secret\":\"[redacted]\"")
                .contains("Bearer [redacted]")
                .doesNotContain("abc123")
                .doesNotContain("super-secret")
                .doesNotContain("eyJhbGciOiJIUzI1NiJ9");
    }

    @Test
    void sanitizeLimitsStoredResponseSizeAfterRedaction() {
        String sanitized = WebhookResponseSanitizer.sanitize("ok".repeat(700));

        assertThat(sanitized).hasSize(1000);
    }
}
