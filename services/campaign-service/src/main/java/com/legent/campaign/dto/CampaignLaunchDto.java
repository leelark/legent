package com.legent.campaign.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class CampaignLaunchDto {

    public enum LaunchAction {
        AUTO,
        PREVIEW,
        SAFE_FIX,
        SUBMIT_APPROVAL,
        SCHEDULE,
        LAUNCH
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaunchPlanRequest {
        @NotBlank
        private String campaignId;

        @NotBlank
        private String idempotencyKey;

        @Builder.Default
        private LaunchAction action = LaunchAction.PREVIEW;

        private Instant scheduledAt;
        private Boolean confirmLaunch;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaunchPlanResponse {
        private String planId;
        private String campaignId;
        private String idempotencyKey;
        private String status;
        private Integer readinessScore;
        private Integer blockerCount;
        private Integer warningCount;
        private String primaryAction;
        private String message;
        private String auditId;
        private Map<String, Object> affectedResourceIds;
        private List<String> blockers;
        private List<String> warnings;
        private List<LaunchRecommendation> recommendations;
        private List<LaunchStepResult> steps;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaunchStepResult {
        private String key;
        private String label;
        private String status;
        private Integer score;
        private String message;
        private Map<String, Object> details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaunchRecommendation {
        private String key;
        private String severity;
        private String title;
        private String detail;
        private Boolean autoFixAvailable;
    }
}
