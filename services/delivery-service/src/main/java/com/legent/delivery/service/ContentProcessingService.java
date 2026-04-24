package com.legent.delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentProcessingService {

    @Value("${legent.tracking.base-url}")
    private String trackingBaseUrl;

    private final TrackingUrlSigner trackingUrlSigner;

    @PostConstruct
    public void validateConfiguration() {
        if (trackingBaseUrl == null || trackingBaseUrl.isBlank()) {
            throw new IllegalStateException("Required configuration 'legent.tracking.base-url' is not set. " +
                    "Please configure the tracking base URL (e.g., http://localhost:8080)");
        }
        // Validate URL format
        try {
            java.net.URI uri = new java.net.URI(trackingBaseUrl);
            if (uri.getScheme() == null || (!uri.getScheme().equals("http") && !uri.getScheme().equals("https"))) {
                throw new IllegalStateException("Invalid tracking base URL scheme. Must be http or https: " + trackingBaseUrl);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalStateException("Invalid tracking base URL - missing host: " + trackingBaseUrl);
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Invalid tracking base URL format: " + trackingBaseUrl, e);
        }
        log.info("ContentProcessingService initialized with tracking base URL: {}", trackingBaseUrl);
    }

    private static final Pattern LINK_PATTERN = Pattern.compile("<a\\s+(?:[^>]*?\\s+)?href=([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE);

    public String processContent(String htmlContent, String tenantId, String campaignId, String subscriberId, String messageId) {
        if (htmlContent == null) return null;

        String processed = injectTrackingPixel(htmlContent, tenantId, campaignId, subscriberId, messageId);
        processed = rewriteLinks(processed, tenantId, campaignId, subscriberId, messageId);

        return processed;
    }

    private String injectTrackingPixel(String html, String t, String c, String s, String m) {
        if (trackingBaseUrl == null || trackingBaseUrl.isBlank()) {
            throw new IllegalStateException("Tracking base URL not configured");
        }
        // Generate HMAC signature to prevent URL tampering
        String sig = trackingUrlSigner.generateSignature(t, c, s, m);
        String pixelUrl = String.format("%s/api/v1/tracking/o.gif?t=%s&c=%s&s=%s&m=%s&sig=%s",
                trackingBaseUrl, t, c, s, m, sig);
        String pixelTag = String.format("<img src=\"%s\" width=\"1\" height=\"1\" border=\"0\" style=\"display:none;\" />", pixelUrl);

        if (html.toLowerCase().contains("</body>")) {
            return html.replaceFirst("(?i)</body>", pixelTag + "</body>");
        } else {
            return html + pixelTag;
        }
    }

    private String rewriteLinks(String html, String t, String c, String s, String m) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = LINK_PATTERN.matcher(html);
        int lastEnd = 0;

        while (matcher.find()) {
            sb.append(html, lastEnd, matcher.start());
            String originalUrl = matcher.group(2);
            
            // Skip anchor links and tracking links already processed
            if (originalUrl.startsWith("#") || originalUrl.contains("/api/v1/tracking/c")) {
                sb.append(matcher.group(0));
            } else {
                // Generate HMAC signature to prevent URL tampering
                String sig = trackingUrlSigner.generateSignature(t, c, s, m);
                String trackedUrl = String.format("%s/api/v1/tracking/c?url=%s&t=%s&c=%s&s=%s&m=%s&sig=%s",
                        trackingBaseUrl, java.net.URLEncoder.encode(originalUrl, java.nio.charset.StandardCharsets.UTF_8),
                        t, c, s, m, sig);
                
                String fullTag = matcher.group(0).replace(originalUrl, trackedUrl);
                sb.append(fullTag);
            }
            lastEnd = matcher.end();
        }
        sb.append(html.substring(lastEnd));
        return sb.toString();
    }
}
