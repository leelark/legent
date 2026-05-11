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
        private PublicationCalendar publicationCalendar;
        private List<BlackoutWindow> blackoutWindows;
        private List<LaunchDependency> dependencies;
        private SendClassification sendClassification;
        private BudgetGuard budgetGuard;
        private FrequencyGuard frequencyGuard;
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
        private Map<String, Object> launchControls;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublicationCalendar {
        private String calendarId;
        private String timezone;
        private Instant publishAfter;
        private Instant publishBefore;
        private List<String> allowedDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlackoutWindow {
        private String name;
        private Instant startsAt;
        private Instant endsAt;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LaunchDependency {
        private String key;
        private String resourceType;
        private String resourceId;
        private Boolean satisfied;
        private String detail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendClassification {
        private String classificationKey;
        private String senderProfileId;
        private String deliveryProfileId;
        private String unsubscribePolicy;
        private Boolean commercial;
        private Boolean requiresConsent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetGuard {
        private Boolean enforced;
        private Long estimatedRecipients;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FrequencyGuard {
        private Boolean enforceWorkspacePolicy;
        private Integer maxSends;
        private Integer windowHours;
        private Boolean includeJourneys;
    }
}
