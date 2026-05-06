package com.legent.tracking.service;

import com.legent.cache.service.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingUrlVerifierTest {

    @Test
    void verifiesDeterministicallyWhenCacheKeyIsMissing() throws Exception {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.get(anyString(), eq(String.class))).thenReturn(Optional.empty());
        TrackingUrlVerifier verifier = new TrackingUrlVerifier(cacheService);
        ReflectionTestUtils.setField(verifier, "globalSigningKey", "test-signing-key-that-is-long-enough");

        String valid = sign("test-signing-key-that-is-long-enough", "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a");

        assertTrue(verifier.verifyClickSignature(valid, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a"));
        assertFalse(verifier.verifyClickSignature(valid, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/b"));
    }

    private String sign(String globalKey, String tenantId, String campaignId, String subscriberId, String messageId, String workspaceId, String url) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String signingKey = Base64.getEncoder().encodeToString(digest.digest((globalKey + ":" + tenantId).getBytes(StandardCharsets.UTF_8)));
        String data = String.join(":", tenantId, campaignId, subscriberId, messageId, workspaceId, url);

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
