package com.legent.tracking.controller;

import com.legent.tracking.service.TrackingService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class TrackingControllerTest {
    @Test
    void trackOpen_returnsGif() throws Exception {
        var service = Mockito.mock(TrackingService.class);
        var controller = new TrackingController(service);
        var response = new MockHttpServletResponse();
        controller.trackOpen("mid123", "tenant123", response);
        assertEquals("image/gif", response.getContentType());
        assertTrue(response.getContentAsByteArray().length > 0);
    }

    @Test
    void trackClick_redirects() throws Exception {
        var service = Mockito.mock(TrackingService.class);
        var controller = new TrackingController(service);
        var response = new MockHttpServletResponse();
        controller.trackClick("mid123", "https://example.com", "tenant123", response);
        assertEquals("https://example.com", response.getRedirectedUrl());
    }
}
