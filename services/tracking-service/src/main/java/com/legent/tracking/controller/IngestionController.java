package com.legent.tracking.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.service.TrackingIngestionService;
import com.legent.tracking.service.TrackingUrlVerifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
@Validated
public class IngestionController {

    private final TrackingIngestionService ingestionService;
    private final TrackingUrlVerifier trackingUrlVerifier;

    // A transparent 1x1 GIF byte array
    private static final byte[] PIXEL_BYTES = new byte[]{
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00, 0x01, 0x00,
            (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xff, (byte) 0xff, (byte) 0xff, 0x21,
            (byte) 0xf9, 0x04, 0x01, 0x00, 0x00, 0x00, 0x00, 0x2c, 0x00, 0x00,
            0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02, 0x44,
            0x01, 0x00, 0x3b
    };

    @GetMapping("/o.gif")
    public ResponseEntity<byte[]> trackOpen(
            @RequestParam @NotBlank String t, // tenantId
            @RequestParam @NotBlank String c, // campaignId
            @RequestParam @NotBlank String s, // subscriberId
            @RequestParam @NotBlank String m, // messageId
            @RequestParam @NotBlank String sig, // HMAC signature
            HttpServletRequest request) {

        // Verify HMAC signature to prevent event forgery
        if (!trackingUrlVerifier.verifySignature(sig, t, c, s, m)) {
            log.warn("Invalid tracking signature for open event: t={}, c={}, s={}, m={}", t, c, s, m);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        ingestionService.processOpen(t, c, s, m, request.getHeader("User-Agent"), getClientIp(request));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_GIF);
        headers.setCacheControl("no-cache, no-store, must-revalidate");
        headers.setPragma("no-cache");
        headers.setExpires(0);

        return new ResponseEntity<>(PIXEL_BYTES, headers, HttpStatus.OK);
    }

    @GetMapping("/c")
    public ResponseEntity<Void> trackClick(
            @RequestParam @NotBlank String url,
            @RequestParam @NotBlank String t, // tenantId
            @RequestParam @NotBlank String c, // campaignId
            @RequestParam @NotBlank String s, // subscriberId
            @RequestParam @NotBlank String m, // messageId
            @RequestParam @NotBlank String sig, // HMAC signature
            HttpServletRequest request) {

        // Verify HMAC signature to prevent event forgery
        if (!trackingUrlVerifier.verifyClickSignature(sig, t, c, s, m, url)) {
            log.warn("Invalid tracking signature for click event: t={}, c={}, s={}, m={}", t, c, s, m);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https")) || host == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        ingestionService.processClick(t, c, s, m, url, request.getHeader("User-Agent"), getClientIp(request));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> trackConversion(
            @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
            @RequestBody @Valid TrackingDto.ConversionRequest requestPayload,
            HttpServletRequest request) {
        
        ingestionService.processConversion(tenantId, requestPayload, request.getHeader("User-Agent"), getClientIp(request));
        return ApiResponse.ok(null);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
