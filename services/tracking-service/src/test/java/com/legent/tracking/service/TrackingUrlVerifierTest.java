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

    @Test
    void acceptsPreviousSigningKeyWhenDeliveryOverlapKeyExists() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.get(anyString(), eq(String.class))).thenReturn(Optional.empty());

        String globalKey = "test-signing-key-that-is-long-enough";
        String previousKey = TrackingSignatureSupport.deriveTenantSigningKey(globalKey, "tenant-1", 1);
        when(cacheService.get(eq("tracking:signing-key:prev:tenant-1"), eq(String.class))).thenReturn(Optional.of(previousKey));

        TrackingUrlVerifier verifier = new TrackingUrlVerifier(cacheService);
        ReflectionTestUtils.setField(verifier, "globalSigningKey", globalKey);
        ReflectionTestUtils.setField(verifier, "currentKeyVersion", 2);

        String openSignature = sign(globalKey, 1, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", null);
        String clickSignature = sign(globalKey, 1, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a");

        assertTrue(verifier.verifySignature(openSignature, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1"));
        assertTrue(verifier.verifyClickSignature(clickSignature, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a"));
        assertFalse(verifier.verifySignature(openSignature, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-2"));
        assertFalse(verifier.verifyClickSignature(clickSignature, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/b"));
        assertFalse(verifier.verifyClickSignature(clickSignature, "tenant-2", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a"));
    }

    @Test
    void rejectsPreviousSigningKeyWhenOverlapKeyIsMissing() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.get(anyString(), eq(String.class))).thenReturn(Optional.empty());
        TrackingUrlVerifier verifier = new TrackingUrlVerifier(cacheService);
        ReflectionTestUtils.setField(verifier, "globalSigningKey", "test-signing-key-that-is-long-enough");
        ReflectionTestUtils.setField(verifier, "currentKeyVersion", 2);

        String oldSignature = sign("test-signing-key-that-is-long-enough", 1, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a");

        assertFalse(verifier.verifyClickSignature(oldSignature, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a"));
    }

    private String sign(String globalKey, String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url) {
        return sign(globalKey, 1, tenantId, campaignId, subscriberId, messageId, workspaceId, url);
    }

    private String sign(String globalKey, int keyVersion, String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url) {
        String signingKey = TrackingSignatureSupport.deriveTenantSigningKey(globalKey, tenantId, keyVersion);
        return TrackingSignatureSupport.sign(signingKey, tenantId, campaignId, subscriberId, messageId, workspaceId, url);
    }
}
