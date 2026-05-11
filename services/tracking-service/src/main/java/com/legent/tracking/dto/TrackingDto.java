package com.legent.tracking.dto;

import java.math.BigDecimal;
import java.util.List;
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
        private String experimentId;
        private String variantId;
        private Boolean holdout;
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
        private String experimentId;
        private String variantId;
        @NotBlank
        private String eventName;
        private BigDecimal value; // AUDIT-027: Use BigDecimal instead of Double for monetary precision
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventExportRequest {
        private String campaignId;
        private List<String> eventTypes;
        private Instant startAt;
        private Instant endAt;
        private Integer limit;
        private String format;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventExportResponse {
        private String format;
        private int rowCount;
        private String content;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RollupResponse {
        private String campaignId;
        private String grain;
        private List<Map<String, Object>> rows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaxonomyEntry {
        private String eventType;
        private String category;
        private String description;
        private List<String> requiredFields;
        private List<String> metadataKeys;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationResponse {
        private String campaignId;
        private Map<String, Object> summaryCounts;
        private Map<String, Object> rawEventCounts;
        private List<String> mismatches;
        private boolean reconciled;
    }
}
