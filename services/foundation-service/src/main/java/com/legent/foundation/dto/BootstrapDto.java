package com.legent.foundation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

public class BootstrapDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusResponse {
        private String tenantId;
        private String workspaceId;
        private String environmentId;
        private String status;
        private String message;
        private int retryCount;
        private Instant lastAttemptAt;
        private Instant completedAt;
        private Map<String, Object> modules;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepairRequest {
        private boolean force;
    }
}
