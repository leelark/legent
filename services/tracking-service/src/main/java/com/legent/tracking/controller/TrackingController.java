package com.legent.tracking.controller;

import com.legent.tracking.dto.TrackingDto;
import jakarta.servlet.http.HttpServletRequest;
import com.legent.tracking.service.TrackingIngestionService;
import jakarta.validation.Valid;
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
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        trackingIngestionService.processOpen(
                tenantId,
                campaignId,
                subscriberId,
                mid,
                userAgent,
                clientIp(request)
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
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request,
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
                userAgent,
                clientIp(request)
        );
        response.sendRedirect(url);
    }

    // Conversion event endpoint
    @PostMapping("/conversion")
    public void trackConversion(
            @RequestParam(required = false) String mid,
            @RequestParam("t") @NotBlank String tenantId,
            @Valid @RequestBody TrackingDto.ConversionRequest conversion,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest request) {
        if ((conversion.getMessageId() == null || conversion.getMessageId().isBlank()) && mid != null && !mid.isBlank()) {
            conversion.setMessageId(mid);
        }
        trackingIngestionService.processConversion(tenantId, conversion, userAgent, clientIp(request));
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
