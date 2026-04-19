package com.legent.campaign.dto;

import java.util.List;

import java.time.Instant;

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
        private String name;
        private String subject;
        private String preheader;
        private String senderProfileId;
        private CampaignStatus status;
        private CampaignType type;
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
        @NotNull
        private CampaignType type;
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
}
