package com.legent.delivery.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentProcessingServiceTest {

    private TrackingUrlSigner trackingUrlSigner;
    private ContentProcessingService service;

    @BeforeEach
    void setUp() {
        trackingUrlSigner = mock(TrackingUrlSigner.class);
        when(trackingUrlSigner.generateSignature(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("open-signature");
        when(trackingUrlSigner.generateClickSignature(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("click-signature");
        service = new ContentProcessingService(trackingUrlSigner);
        ReflectionTestUtils.setField(service, "trackingBaseUrl", "https://track.legent.test");
    }

    @Test
    void processContent_tracksPublicHttpAndHttpsClickDestinations() {
        String html = """
                <html><body>
                <a href="http://93.184.216.34/path?q=1">Public HTTP</a>
                <a href="https://93.184.216.34/secure">Public HTTPS</a>
                </body></html>
                """;

        String processed = process(html);

        assertEquals(2, countOccurrences(processed, "/api/v1/tracking/c?url="));
        assertTrue(processed.contains("url=http%3A%2F%2F93.184.216.34%2Fpath%3Fq%3D1"));
        assertTrue(processed.contains("url=https%3A%2F%2F93.184.216.34%2Fsecure"));
        verify(trackingUrlSigner).generateClickSignature(
                "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1",
                "http://93.184.216.34/path?q=1");
        verify(trackingUrlSigner).generateClickSignature(
                "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1",
                "https://93.184.216.34/secure");
    }

    @Test
    void processContent_tracksUnresolvedPublicHostnamesWithoutDnsResolution() {
        String html = """
                <html><body>
                <a href="https://example.invalid/path?q=1">Unresolved public hostname</a>
                </body></html>
                """;

        String processed = process(html);

        assertEquals(1, countOccurrences(processed, "/api/v1/tracking/c?url="));
        assertTrue(processed.contains("url=https%3A%2F%2Fexample.invalid%2Fpath%3Fq%3D1"));
        verify(trackingUrlSigner).generateClickSignature(
                "tenant-1", "campaign-1", "subscriber-1", "message-1", "workspace-1",
                "https://example.invalid/path?q=1");
    }

    @Test
    void processContent_rejectsUnsafeClickDestinations() {
        assertUnsafeDestinationRejected("http://localhost/admin");
        assertUnsafeDestinationRejected("https://10.0.0.5/internal");
        assertUnsafeDestinationRejected("http://169.254.169.254/latest/meta-data");
        assertUnsafeDestinationRejected("https://metadata.google.internal/computeMetadata/v1/");
    }

    @Test
    void processContent_leavesNonHttpAnchorsUntracked() {
        String html = """
                <html><body>
                <a href="mailto:security@example.com">Mail</a>
                <a href="tel:+15551234567">Call</a>
                <a href="/relative/path">Relative</a>
                </body></html>
                """;

        String processed = process(html);

        assertEquals(0, countOccurrences(processed, "/api/v1/tracking/c?url="));
        assertTrue(processed.contains("href=\"mailto:security@example.com\""));
        assertTrue(processed.contains("href=\"tel:+15551234567\""));
        assertTrue(processed.contains("href=\"/relative/path\""));
        verify(trackingUrlSigner, never()).generateClickSignature(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private String process(String html) {
        return service.processContent(
                html,
                "tenant-1",
                "campaign-1",
                "subscriber-1",
                "message-1",
                "workspace-1");
    }

    private void assertUnsafeDestinationRejected(String url) {
        String html = "<html><body><a href=\"" + url + "\">Unsafe</a></body></html>";

        assertThrows(IllegalArgumentException.class, () -> process(html));
        verify(trackingUrlSigner, never()).generateClickSignature(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
