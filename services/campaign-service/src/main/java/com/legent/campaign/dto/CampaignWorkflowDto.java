package com.legent.campaign.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class CampaignWorkflowDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitApprovalRequest {
        private String comments;
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
    public static class CampaignApprovalResponse {
        private String id;
        private String campaignId;
        private String requestedBy;
        private String requestedAt;
        private String status;
        private String approvedBy;
        private String approvedAt;
        private String rejectionReason;
        private String comments;
        private String updatedAt;
    }
}
