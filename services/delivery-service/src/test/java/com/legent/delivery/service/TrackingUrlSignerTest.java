package com.legent.delivery.service;

import com.legent.cache.service.CacheService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackingUrlSignerTest {

    @Test
    void clickSignatureBindsDestinationUrl() {
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.get(anyString(), eq(String.class))).thenReturn(Optional.empty());
        TrackingUrlSigner signer = new TrackingUrlSigner(cacheService);
        ReflectionTestUtils.setField(signer, "globalSigningKey", "test-signing-key-that-is-long-enough");
        ReflectionTestUtils.setField(signer, "currentKeyVersion", 1);

        String first = signer.generateClickSignature("tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a");
        String second = signer.generateClickSignature("tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/b");

        assertNotEquals(first, second);
        assertTrue(signer.verifyClickSignature(first, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/a"));
        assertFalse(signer.verifyClickSignature(first, "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1", "https://example.com/b"));
    }
}
