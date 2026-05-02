package com.legent.tracking.dto;

import java.util.Map;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class TrackingDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RawEventPayload {
        private String id;
        private String tenantId;
        private String eventType;
        private String campaignId;
        private String subscriberId;
        private String messageId;
        private String userAgent;
        private String ipAddress;
        private String linkUrl;
        private Instant timestamp;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionRequest {
        private String messageId;
        private String email;
        private String subscriberId;
        private String campaignId;
        @NotBlank
        private String eventName;
        private Double value;
        private String currency;
    }
}
