package com.legent.automation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AutomationStudioDto {

    public enum ActivityType {
        SQL_QUERY,
        FILE_DROP,
        IMPORT,
        EXTRACT,
        SCRIPT,
        WEBHOOK
    }

    public enum ActivityStatus {
        DRAFT,
        ACTIVE,
        PAUSED,
        ARCHIVED
    }

    public enum RunStatus {
        VERIFIED,
        SUCCEEDED,
        FAILED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityRequest {
        @NotBlank
        private String name;
        @NotNull
        private ActivityType activityType;
        private ActivityStatus status;
        private String scheduleExpression;
        private Map<String, Object> inputConfig;
        private Map<String, Object> outputConfig;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityResponse {
        private String id;
        private String name;
        private ActivityType activityType;
        private ActivityStatus status;
        private String scheduleExpression;
        private Map<String, Object> inputConfig;
        private Map<String, Object> outputConfig;
        private Map<String, Object> verification;
        private Instant lastRunAt;
        private Instant nextRunAt;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationResponse {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        private Map<String, Object> normalizedConfig;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunRequest {
        private boolean dryRun;
        private String triggerSource;
        private Map<String, Object> overrides;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunResponse {
        private String id;
        private String activityId;
        private RunStatus status;
        private boolean dryRun;
        private String triggerSource;
        private Long rowsRead;
        private Long rowsWritten;
        private String errorMessage;
        private Map<String, Object> result;
        private Instant startedAt;
        private Instant completedAt;
        private Instant createdAt;
    }
}
