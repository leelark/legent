package com.legent.automation.dto;

import com.legent.automation.domain.AutomationArtifact;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
        WEBHOOK,
        NOTIFICATION,
        SEND_EMAIL
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

    public enum FailurePolicy {
        STOP_ON_FAILURE,
        SKIP_DEPENDENTS,
        CONTINUE_INDEPENDENT
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
        private List<String> dependencyActivityIds;
        private FailurePolicy failurePolicy;
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
        private List<String> dependencyActivityIds;
        private FailurePolicy failurePolicy;
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
        private Boolean dryRun;
        private Boolean confirmLiveRun;
        private String idempotencyKey;
        private String triggerSource;
        private Map<String, Object> overrides;

        public boolean isDryRun() {
            return !Boolean.FALSE.equals(dryRun);
        }

        public boolean isLiveRunConfirmed() {
            return Boolean.TRUE.equals(confirmLiveRun);
        }
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
        private String traceId;
        private String errorCode;
        private String errorMessage;
        private String idempotencyKey;
        private Map<String, Object> dependencyTrace;
        private Map<String, Object> result;
        private Instant startedAt;
        private Instant completedAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArtifactRequest {
        private String activityId;
        private AutomationArtifact.SourceKind sourceKind;
        private AutomationArtifact.ArtifactStatus status;
        @NotBlank
        private String displayName;
        @NotBlank
        private String contentType;
        @NotNull
        @Positive
        private Long sizeBytes;
        @NotBlank
        private String sha256;
        private String retentionPolicy;
        private Instant expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArtifactResponse {
        private String artifactId;
        private String activityId;
        private AutomationArtifact.SourceKind sourceKind;
        private AutomationArtifact.ArtifactStatus status;
        private String displayName;
        private String contentType;
        private Long sizeBytes;
        private String sha256;
        private String retentionPolicy;
        private Instant expiresAt;
        private Instant createdAt;
    }
}
