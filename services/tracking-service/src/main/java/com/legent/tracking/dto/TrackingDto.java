package com.legent.tracking.dto;

import java.math.BigDecimal;
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
        private String workspaceId;
        private String teamId;
        private String environmentId;
        private String actorId;
        private String ownershipScope;
        private String idempotencyKey;
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
        private BigDecimal value; // AUDIT-027: Use BigDecimal instead of Double for monetary precision
        private String currency;
    }
}
