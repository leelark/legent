package com.legent.campaign.dto;

import java.time.Instant;

import com.legent.campaign.domain.SendBatch.BatchStatus;
import com.legent.campaign.domain.SendJob.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class SendJobDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String id;
        private String tenantId;
        private String workspaceId;
        private String teamId;
        private String ownershipScope;
        private String campaignId;
        private JobStatus status;
        private Instant scheduledAt;
        private Instant startedAt;
        private Instant completedAt;
        private Instant pausedAt;
        private Instant cancelledAt;
        private Long totalTarget;
        private Long totalSent;
        private Long totalFailed;
        private Long totalBounced;
        private Long totalSuppressed;
        private String errorMessage;
        private String triggerSource;
        private String triggerReference;
        private String idempotencyKey;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerRequest {
        private Instant scheduledAt;
        private String triggerSource;
        private String triggerReference;
        private String idempotencyKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchResponse {
        private String id;
        private String tenantId;
        private String workspaceId;
        private String jobId;
        private BatchStatus status;
        private String domain;
        private Integer batchSize;
        private Integer processedCount;
        private Integer successCount;
        private Integer failureCount;
        private Integer retryCount;
        private String lastError;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
