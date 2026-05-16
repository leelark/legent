package com.legent.tracking.service;

import com.legent.cache.service.CacheService;
import com.legent.common.tracking.TrackingSignatureSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingUrlVerifierTest {

    @Test
    void verifiesDeterministicallyWhenCacheKeyIsMissing() throws Exception {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.get(anyString(), eq(String.class))).thenReturn(Optional.empty());
        TrackingUrlVerifier verifier = new TrackingUrlVerifier(cacheService);
        ReflectionTestUtils.setField(verifier, "globalSigningKey", "test-signing-key-that-is-long-enough");
        ReflectionTestUtils.setField(verifier, "currentKeyVersion", 1);

        String valid = sign("test-signing-key-that-is-long-enough", "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a");

        assertTrue(verifier.verifyClickSignature(valid, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a"));
        assertFalse(verifier.verifyClickSignature(valid, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/b"));
        verify(cacheService, times(2)).get("tracking:signing-key:1:tenant-1", String.class);
    }

    @Test
    void rejectsWorkspaceLessLegacySignatureWhenWorkspaceIsPresent() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.get(anyString(), eq(String.class))).thenReturn(Optional.empty());
        TrackingUrlVerifier verifier = new TrackingUrlVerifier(cacheService);
        ReflectionTestUtils.setField(verifier, "globalSigningKey", "test-signing-key-that-is-long-enough");
        ReflectionTestUtils.setField(verifier, "currentKeyVersion", 1);

        String legacy = sign("test-signing-key-that-is-long-enough", "tenant-1", "campaign-1", "subscriber-1", "message-1", null, "https://example.com/a");

        assertFalse(verifier.verifyClickSignature(legacy, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a"));
    }

    private String sign(String globalKey, String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url) {
        String signingKey = TrackingSignatureSupport.deriveTenantSigningKey(globalKey, tenantId, 1);
        return TrackingSignatureSupport.sign(signingKey, tenantId, campaignId, subscriberId, messageId, workspaceId, url);
    }
}
