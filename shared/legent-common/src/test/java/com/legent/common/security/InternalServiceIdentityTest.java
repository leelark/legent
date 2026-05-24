package com.legent.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalServiceIdentityTest {

    private static final String CREDENTIAL = "internal-service-token-1234567890abcdef";
    private static final Instant NOW = Instant.parse("2026-05-24T10:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void signsAndMatchesAllowedServiceForScopedAction() {
        String signature = InternalServiceIdentity.sign(
                CREDENTIAL,
                "campaign-service",
                "tenant-1",
                "workspace-1",
                chunkAction("job-1", "chunk-1"),
                NOW);

        assertThat(InternalServiceIdentity.matches(
                CREDENTIAL,
                CREDENTIAL,
                "campaign-service",
                Set.of("campaign-service"),
                "tenant-1",
                "workspace-1",
                chunkAction("job-1", "chunk-1"),
                NOW.toString(),
                signature,
                CLOCK,
                Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void rejectsWrongServiceScopeActionAndExpiredTimestamp() {
        String currentSignature = InternalServiceIdentity.sign(
                CREDENTIAL,
                "campaign-service",
                "tenant-1",
                "workspace-1",
                chunkAction("job-1", "chunk-1"),
                NOW);
        String expiredSignature = InternalServiceIdentity.sign(
                CREDENTIAL,
                "campaign-service",
                "tenant-1",
                "workspace-1",
                chunkAction("job-1", "chunk-1"),
                NOW.minus(Duration.ofMinutes(10)));

        assertThat(InternalServiceIdentity.matches(
                CREDENTIAL,
                CREDENTIAL,
                "automation-service",
                Set.of("campaign-service"),
                "tenant-1",
                "workspace-1",
                chunkAction("job-1", "chunk-1"),
                NOW.toString(),
                currentSignature,
                CLOCK,
                Duration.ofMinutes(5))).isFalse();
        assertThat(InternalServiceIdentity.matches(
                CREDENTIAL,
                CREDENTIAL,
                "campaign-service",
                Set.of("campaign-service"),
                "tenant-2",
                "workspace-1",
                chunkAction("job-1", "chunk-1"),
                NOW.toString(),
                currentSignature,
                CLOCK,
                Duration.ofMinutes(5))).isFalse();
        assertThat(InternalServiceIdentity.matches(
                CREDENTIAL,
                CREDENTIAL,
                "campaign-service",
                Set.of("campaign-service"),
                "tenant-1",
                "workspace-1",
                chunkAction("job-1", "chunk-2"),
                NOW.toString(),
                currentSignature,
                CLOCK,
                Duration.ofMinutes(5))).isFalse();
        assertThat(InternalServiceIdentity.matches(
                CREDENTIAL,
                CREDENTIAL,
                "campaign-service",
                Set.of("campaign-service"),
                "tenant-1",
                "workspace-1",
                chunkAction("job-1", "chunk-1"),
                NOW.minus(Duration.ofMinutes(10)).toString(),
                expiredSignature,
                CLOCK,
                Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void rejectsMissingFieldsBeforeSigning() {
        assertThatThrownBy(() -> InternalServiceIdentity.sign(
                CREDENTIAL,
                " ",
                "tenant-1",
                "workspace-1",
                "action",
                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceName");
    }

    private String chunkAction(String jobId, String chunkId) {
        return InternalServiceIdentity.scopedAction(
                InternalServiceIdentity.ACTION_AUDIENCE_RESOLUTION_CHUNK_READ,
                jobId,
                chunkId);
    }
}
