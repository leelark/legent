package com.legent.common.tracking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingSignatureSupportTest {

    @Test
    void signatureBindsWorkspaceAndUrl() {
        String key = TrackingSignatureSupport.deriveTenantSigningKey(
                "test-signing-key-that-is-long-enough", "tenant-1", 1);

        String signature = TrackingSignatureSupport.sign(
                key, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a");

        assertThat(TrackingSignatureSupport.verify(
                signature, key, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a"))
                .isTrue();
        assertThat(TrackingSignatureSupport.verify(
                signature, key, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-2", "https://example.com/a"))
                .isFalse();
        assertThat(TrackingSignatureSupport.verify(
                signature, key, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/b"))
                .isFalse();
    }
}
