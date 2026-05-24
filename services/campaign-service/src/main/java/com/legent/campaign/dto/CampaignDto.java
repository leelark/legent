package com.legent.campaign.dto;

import java.util.List;
import java.util.Map;

import java.time.Instant;
import java.time.LocalTime;

import com.legent.campaign.domain.Campaign.CampaignStatus;
import com.legent.campaign.domain.Campaign.CampaignType;
import com.legent.campaign.domain.CampaignAudience.AudienceAction;
import com.legent.campaign.domain.CampaignAudience.AudienceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class CampaignDto {

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
        private String name;
        private String subject;
        private String preheader;
        private String senderProfileId;
        private String sendGovernancePolicyId;
        private String templateId;
        private String senderName;
        private String senderEmail;
        private String replyToEmail;
        private String brandId;
        private Boolean trackingEnabled;
        private Boolean complianceEnabled;
        private String providerId;
        private String sendingDomain;
        private String timezone;
        private LocalTime quietHoursStart;
        private LocalTime quietHoursEnd;
        private LocalTime sendWindowStart;
        private LocalTime sendWindowEnd;
        private Integer frequencyCap;
        private Boolean approvalRequired;
        private String approvedBy;
        private Instant approvedAt;
        private Instant archivedAt;
        private String lifecycleNote;
        private String triggerSource;
        private String triggerReference;
        private String experimentConfig;
        private CampaignStatus status;
        private CampaignType type;
        private Instant scheduledAt;
        private String sendTimeOptimizationPolicyKey;
        private String sendTimeOptimizationType;
        private String sendTimeOptimizationRunId;
        private String sendTimeOptimizationSnapshotHash;
        private Instant sendTimeOptimizationOriginalScheduledAt;
        private Instant sendTimeOptimizationRecommendedScheduledAt;
        private String sendTimeOptimizationTimezone;
        private String sendTimeOptimizationConfidenceBand;
        private String sendTimeOptimizationFallbackMode;
        private List<String> sendTimeOptimizationBlockedReasons;
        private List<String> sendTimeOptimizationDataQualityReasons;
        private List<String> sendTimeOptimizationReasonCodes;
        private Boolean sendTimeOptimizationApprovalRequired;
        private Boolean sendTimeOptimizationRollbackRequired;
        private Boolean sendTimeOptimizationApproved;
        private String sendTimeOptimizationApprovalId;
        private String sendTimeOptimizationApprovedBy;
        private Instant sendTimeOptimizationApprovedAt;
        private String sendTimeOptimizationRollbackSnapshotId;
        private Boolean sendTimeOptimizationQuietHoursGatePassed;
        private Boolean sendTimeOptimizationApprovalGatePassed;
        private Boolean sendTimeOptimizationSuppressionGatePassed;
        private Boolean sendTimeOptimizationWarmupGatePassed;
        private Boolean sendTimeOptimizationRateLimitGatePassed;
        private Boolean sendTimeOptimizationProviderCapacityGatePassed;
        private Boolean sendTimeOptimizationDeliverabilityGatePassed;
        private List<AudienceResponse> audiences;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceResponse {
        private String id;
        private AudienceType audienceType;
        private String audienceId;
        private AudienceAction action;
        private String workspaceId;
        private String teamId;
        private String ownershipScope;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank
        private String name;
        private String subject;
        private String preheader;
        private String senderProfileId;
        private String sendGovernancePolicyId;
        private String senderName;
        private String senderEmail;
        private String replyToEmail;
        private String brandId;
        private Boolean trackingEnabled;
        private Boolean complianceEnabled;
        private String providerId;
        private String sendingDomain;
        private String timezone;
        private LocalTime quietHoursStart;
        private LocalTime quietHoursEnd;
        private LocalTime sendWindowStart;
        private LocalTime sendWindowEnd;
        private Integer frequencyCap;
        private Boolean approvalRequired;
        private String triggerSource;
        private String triggerReference;
        private String experimentConfig;
        private String templateId;
        @Builder.Default
        private CampaignType type = CampaignType.STANDARD;
        private List<AudienceRequest> audiences;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @NotBlank
        private String name;
        private String subject;
        private String preheader;
        private String senderProfileId;
        private String sendGovernancePolicyId;
        private String senderName;
        private String senderEmail;
        private String replyToEmail;
        private String brandId;
        private Boolean trackingEnabled;
        private Boolean complianceEnabled;
        private String providerId;
        private String sendingDomain;
        private String timezone;
        private LocalTime quietHoursStart;
        private LocalTime quietHoursEnd;
        private LocalTime sendWindowStart;
        private LocalTime sendWindowEnd;
        private Integer frequencyCap;
        private String lifecycleNote;
        private String experimentConfig;
        private List<AudienceRequest> audiences;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceRequest {
        @NotNull
        private AudienceType audienceType;
        @NotBlank
        private String audienceId;
        @NotNull
        private AudienceAction action;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LifecycleActionRequest {
        private String reason;
        private String comments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecurrenceRequest {
        @NotBlank
        private String frequency; // DAILY, WEEKLY, MONTHLY
        private Integer interval;
        private List<Integer> daysOfWeek;
        private Integer dayOfMonth;
        private Instant endsAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleRequest {
        private Instant scheduledAt;
        private RecurrenceRequest recurrence;
        private SendTimeOptimizationDecision sendTimeOptimization;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendTimeOptimizationDecision {
        private String optimizationType;
        private String policyKey;
        private String optimizationRunId;
        private String snapshotHash;
        private Instant originalScheduledAt;
        private Instant recommendedScheduledAt;
        private String timezone;
        private String confidenceBand;
        private String fallbackMode;
        private List<String> blockedReasons;
        private List<String> dataQualityReasons;
        private List<String> reasonCodes;
        private Boolean approvalRequired;
        private Boolean rollbackRequired;
        private Boolean approved;
        private String approvalId;
        private String approvedBy;
        private Instant approvedAt;
        private String rollbackSnapshotId;
        private Boolean quietHoursGatePassed;
        private Boolean approvalGatePassed;
        private Boolean suppressionGatePassed;
        private Boolean warmupGatePassed;
        private Boolean rateLimitGatePassed;
        private Boolean providerCapacityGatePassed;
        private Boolean deliverabilityGatePassed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApprovalActionRequest {
        private String comments;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerLaunchRequest {
        private String triggerSource;
        private String triggerReference;
        private String idempotencyKey;
        private Instant scheduledAt;
        private Map<String, Object> metadata;
    }
}
