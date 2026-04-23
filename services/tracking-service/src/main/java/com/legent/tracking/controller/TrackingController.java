package com.legent.tracking.controller;

import com.legent.tracking.service.TrackingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/track")
@RequiredArgsConstructor
public class TrackingController {
    private final TrackingService trackingService;

    // Tracking pixel endpoint
    @GetMapping(value = "/open.gif", produces = MediaType.IMAGE_GIF_VALUE)
    public void trackOpen(@RequestParam String mid, HttpServletResponse response) throws IOException {
        trackingService.handleOpen(mid);
        // 1x1 transparent gif
        response.setContentType("image/gif");
        response.getOutputStream().write(new byte[]{
                71,73,70,56,57,97,1,0,1,0,-128,0,0,0,0,0,-1,-1,-1,33,-7,4,1,0,0,0,0,44,0,0,0,0,1,0,1,0,0,2,2,68,1,0,59
        });
        response.getOutputStream().flush();
    }

    // Click tracking endpoint
    @GetMapping("/click")
    public void trackClick(@RequestParam String mid, @RequestParam String url, HttpServletResponse response) throws IOException {
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https")) || host == null) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid redirect URL");
                return;
            }
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid redirect URL");
            return;
        }
        trackingService.handleClick(mid, url);
        response.sendRedirect(url);
    }

    // Conversion event endpoint
    @PostMapping("/conversion")
    public void trackConversion(@RequestParam String mid, @RequestBody String payload) {
        trackingService.handleConversion(mid, payload);
    }
}
