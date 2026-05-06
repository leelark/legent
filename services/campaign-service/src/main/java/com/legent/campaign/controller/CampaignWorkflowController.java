package com.legent.campaign.controller;

import com.legent.campaign.domain.CampaignApproval;
import com.legent.campaign.dto.CampaignWorkflowDto;
import com.legent.campaign.service.CampaignWorkflowService;
import com.legent.common.dto.ApiResponse;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
public class CampaignWorkflowController {

    private final CampaignWorkflowService workflowService;

    @PostMapping("/{campaignId}/submit-approval")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignWorkflowDto.CampaignApprovalResponse> submitForApproval(
            @PathVariable String campaignId,
            @RequestBody(required = false) CampaignWorkflowDto.SubmitApprovalRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String comments = request != null ? request.getComments() : null;
        CampaignApproval approval = workflowService.submitForApproval(tenantId, campaignId, comments);
        return ApiResponse.ok(mapApproval(approval));
    }

    @GetMapping("/{campaignId}/approvals")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST', 'VIEWER')")
    public ApiResponse<List<CampaignWorkflowDto.CampaignApprovalResponse>> approvalHistory(@PathVariable String campaignId) {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(workflowService.getCampaignApprovalHistory(tenantId, campaignId).stream().map(this::mapApproval).toList());
    }

    @GetMapping("/approvals/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER', 'ANALYST')")
    public ApiResponse<List<CampaignWorkflowDto.CampaignApprovalResponse>> pendingApprovals() {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(workflowService.getPendingApprovals(tenantId).stream().map(this::mapApproval).toList());
    }

    @PostMapping("/approvals/{approvalId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignWorkflowDto.CampaignApprovalResponse> approve(
            @PathVariable String approvalId,
            @RequestBody(required = false) CampaignWorkflowDto.ApprovalActionRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String comments = request != null ? request.getComments() : null;
        CampaignApproval approval = workflowService.approveCampaign(tenantId, approvalId, comments);
        return ApiResponse.ok(mapApproval(approval));
    }

    @PostMapping("/approvals/{approvalId}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignWorkflowDto.CampaignApprovalResponse> reject(
            @PathVariable String approvalId,
            @RequestBody(required = false) CampaignWorkflowDto.ApprovalActionRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String reason = request != null ? request.getReason() : null;
        String comments = request != null ? request.getComments() : null;
        CampaignApproval approval = workflowService.rejectCampaign(tenantId, approvalId, reason, comments);
        return ApiResponse.ok(mapApproval(approval));
    }

    @PostMapping("/approvals/{approvalId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'CAMPAIGN_MANAGER')")
    public ApiResponse<CampaignWorkflowDto.CampaignApprovalResponse> cancel(@PathVariable String approvalId) {
        String tenantId = TenantContext.requireTenantId();
        CampaignApproval approval = workflowService.cancelApproval(tenantId, approvalId);
        return ApiResponse.ok(mapApproval(approval));
    }

    private CampaignWorkflowDto.CampaignApprovalResponse mapApproval(CampaignApproval approval) {
        return CampaignWorkflowDto.CampaignApprovalResponse.builder()
                .id(approval.getId())
                .campaignId(approval.getCampaignId())
                .requestedBy(approval.getRequestedBy())
                .requestedAt(approval.getRequestedAt() != null ? approval.getRequestedAt().toString() : null)
                .status(approval.getStatus() != null ? approval.getStatus().name() : null)
                .approvedBy(approval.getApprovedBy())
                .approvedAt(approval.getApprovedAt() != null ? approval.getApprovedAt().toString() : null)
                .rejectionReason(approval.getRejectionReason())
                .comments(approval.getComments())
                .updatedAt(approval.getUpdatedAt() != null ? approval.getUpdatedAt().toString() : null)
                .build();
    }
}
