package com.legent.tracking.controller;

import com.legent.tracking.service.TrackingIngestionService;
import com.legent.tracking.service.TrackingUrlVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionControllerTest {

    private TrackingIngestionService ingestionService;
    private TrackingUrlVerifier trackingUrlVerifier;
    private IngestionController controller;

    @BeforeEach
    void setUp() {
        ingestionService = mock(TrackingIngestionService.class);
        trackingUrlVerifier = mock(TrackingUrlVerifier.class);
        controller = new IngestionController(ingestionService, trackingUrlVerifier);
    }

    @Test
    void trackClick_redirectsAndIngestsPublicDestination() {
        String url = "https://93.184.216.34/path";
        when(trackingUrlVerifier.verifyClickSignature("sig", "tenant-1", "campaign-1", "subscriber-1",
                "message-1", "workspace-1", url)).thenReturn(true);

        ResponseEntity<Void> response = controller.trackClick(
                url,
                "tenant-1",
                "campaign-1",
                "subscriber-1",
                "message-1",
                "sig",
                "workspace-1",
                "experiment-1",
                "variant-1",
                false,
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString(url);
        verify(ingestionService).processClick(
                eq("tenant-1"),
                eq("campaign-1"),
                eq("subscriber-1"),
                eq("message-1"),
                eq("experiment-1"),
                eq("variant-1"),
                eq(false),
                eq("workspace-1"),
                anyString(),
                eq(url),
                eq("JUnit"),
                eq("203.0.113.10"));
    }

    @Test
    void trackClick_rejectsPrivateDestinationAfterValidSignature() {
        String url = "https://10.0.0.5/internal";
        when(trackingUrlVerifier.verifyClickSignature("sig", "tenant-1", "campaign-1", "subscriber-1",
                "message-1", "workspace-1", url)).thenReturn(true);

        ResponseEntity<Void> response = controller.trackClick(
                url,
                "tenant-1",
                "campaign-1",
                "subscriber-1",
                "message-1",
                "sig",
                "workspace-1",
                null,
                null,
                false,
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(ingestionService, never()).processClick(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void trackClick_rejectsInvalidSignatureBeforeDestinationValidation() {
        String url = "mailto:security@example.com";
        when(trackingUrlVerifier.verifyClickSignature("bad", "tenant-1", "campaign-1", "subscriber-1",
                "message-1", "workspace-1", url)).thenReturn(false);

        ResponseEntity<Void> response = controller.trackClick(
                url,
                "tenant-1",
                "campaign-1",
                "subscriber-1",
                "message-1",
                "bad",
                "workspace-1",
                null,
                null,
                false,
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(ingestionService, never()).processClick(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "JUnit");
        request.setRemoteAddr("203.0.113.10");
        return request;
    }
}
