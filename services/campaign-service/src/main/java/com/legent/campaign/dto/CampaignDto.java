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
