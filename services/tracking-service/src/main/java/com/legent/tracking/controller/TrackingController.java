package com.legent.tracking.controller;

import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.service.TrackingIngestionService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/track")
@RequiredArgsConstructor
@Validated
public class TrackingController {
    private final TrackingIngestionService trackingIngestionService;

    // Tracking pixel endpoint
    @GetMapping(value = "/open.gif", produces = MediaType.IMAGE_GIF_VALUE)
    public void trackOpen(
            @RequestParam @NotBlank String mid,
            @RequestParam("t") @NotBlank String tenantId,
            @RequestParam(required = false, name = "c") String campaignId,
            @RequestParam(required = false, name = "s") String subscriberId,
            HttpServletResponse response) throws IOException {
        trackingIngestionService.processOpen(
                tenantId,
                campaignId,
                subscriberId,
                mid,
                null,
                null
        );
        // 1x1 transparent gif
        response.setContentType("image/gif");
        response.getOutputStream().write(new byte[]{
                71,73,70,56,57,97,1,0,1,0,-128,0,0,0,0,0,-1,-1,-1,33,-7,4,1,0,0,0,0,44,0,0,0,0,1,0,1,0,0,2,2,68,1,0,59
        });
        response.getOutputStream().flush();
    }

    // Click tracking endpoint
    @GetMapping("/click")
    public void trackClick(
            @RequestParam @NotBlank String mid,
            @RequestParam @NotBlank String url,
            @RequestParam("t") @NotBlank String tenantId,
            @RequestParam(required = false, name = "c") String campaignId,
            @RequestParam(required = false, name = "s") String subscriberId,
            HttpServletResponse response) throws IOException {
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
        trackingIngestionService.processClick(
                tenantId,
                campaignId,
                subscriberId,
                mid,
                url,
                null,
                null
        );
        response.sendRedirect(url);
    }

    // Conversion event endpoint
    @PostMapping("/conversion")
    public void trackConversion(
            @RequestParam String mid, 
            @RequestParam("t") String tenantId,
            @RequestBody String payload) {
        TrackingDto.ConversionRequest request = TrackingDto.ConversionRequest.builder()
                .campaignId(null)
                .subscriberId(null)
                .eventName("conversion")
                .currency(null)
                .value(null)
                .build();
        trackingIngestionService.processConversion(tenantId, request, null, null);
    }
}
