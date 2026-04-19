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
        private String campaignId;
        private JobStatus status;
        private Instant scheduledAt;
        private Instant startedAt;
        private Instant completedAt;
        private Long totalTarget;
        private Long totalSent;
        private Long totalFailed;
        private Long totalBounced;
        private Long totalSuppressed;
        private String errorMessage;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerRequest {
        private Instant scheduledAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchResponse {
        private String id;
        private String tenantId;
        private String jobId;
        private BatchStatus status;
        private String domain;
        private Integer batchSize;
        private Integer processedCount;
        private Integer successCount;
        private Integer failureCount;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
