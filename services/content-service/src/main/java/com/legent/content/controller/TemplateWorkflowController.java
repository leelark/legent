package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.TemplateApproval;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.TemplateVersionDto;
import com.legent.content.dto.TemplateWorkflowDto;
import com.legent.content.service.TemplateVersionService;
import com.legent.content.service.TemplateWorkflowService;
import com.legent.security.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/templates")
@RequiredArgsConstructor
public class TemplateWorkflowController {

    private final TemplateWorkflowService workflowService;
    private final TemplateVersionService versionService;

    @PostMapping("/{templateId}/draft")
    public ApiResponse<Map<String, Object>> saveDraft(
            @PathVariable String templateId,
            @RequestBody(required = false) TemplateWorkflowDto.DraftRequest request) {
        String tenantId = TenantContext.requireTenantId();
        TemplateWorkflowDto.DraftRequest safeRequest = request != null ? request : new TemplateWorkflowDto.DraftRequest();
        EmailTemplate template = workflowService.saveDraft(
                tenantId,
                templateId,
                safeRequest.getSubject(),
                safeRequest.getHtmlContent(),
                safeRequest.getTextContent()
        );
        return ApiResponse.ok(Map.of(
                "templateId", template.getId(),
                "status", template.getStatus().name()
        ));
    }

    @PostMapping("/{templateId}/submit-approval")
    public ApiResponse<TemplateWorkflowDto.TemplateApprovalResponse> submitForApproval(
            @PathVariable String templateId,
            @RequestBody(required = false) TemplateWorkflowDto.SubmitApprovalRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String comments = request != null ? request.getComments() : null;
        TemplateApproval approval = workflowService.submitForApproval(tenantId, templateId, comments);
        return ApiResponse.ok(mapApproval(approval));
    }

    @GetMapping("/{templateId}/approvals")
    public ApiResponse<List<TemplateWorkflowDto.TemplateApprovalResponse>> approvalHistory(@PathVariable String templateId) {
        String tenantId = TenantContext.requireTenantId();
        List<TemplateApproval> approvals = workflowService.getTemplateApprovalHistory(tenantId, templateId);
        return ApiResponse.ok(approvals.stream().map(this::mapApproval).toList());
    }

    @GetMapping("/approvals/pending")
    public ApiResponse<List<TemplateWorkflowDto.TemplateApprovalResponse>> pendingApprovals() {
        String tenantId = TenantContext.requireTenantId();
        List<TemplateApproval> approvals = workflowService.getPendingApprovals(tenantId);
        return ApiResponse.ok(approvals.stream().map(this::mapApproval).toList());
    }

    @PostMapping("/approvals/{approvalId}/approve")
    public ApiResponse<TemplateWorkflowDto.TemplateApprovalResponse> approveTemplate(
            @PathVariable String approvalId,
            @RequestBody(required = false) TemplateWorkflowDto.ApprovalActionRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String comments = request != null ? request.getComments() : null;
        TemplateApproval approval = workflowService.approveTemplate(tenantId, approvalId, comments);
        return ApiResponse.ok(mapApproval(approval));
    }

    @PostMapping("/approvals/{approvalId}/reject")
    public ApiResponse<TemplateWorkflowDto.TemplateApprovalResponse> rejectTemplate(
            @PathVariable String approvalId,
            @RequestBody(required = false) TemplateWorkflowDto.ApprovalActionRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String reason = request != null ? request.getReason() : null;
        TemplateApproval approval = workflowService.rejectTemplate(tenantId, approvalId, reason);
        return ApiResponse.ok(mapApproval(approval));
    }

    @PostMapping("/approvals/{approvalId}/cancel")
    public ApiResponse<TemplateWorkflowDto.TemplateApprovalResponse> cancelApproval(@PathVariable String approvalId) {
        String tenantId = TenantContext.requireTenantId();
        TemplateApproval approval = workflowService.cancelApproval(tenantId, approvalId);
        return ApiResponse.ok(mapApproval(approval));
    }

    @PostMapping("/{templateId}/publish")
    public ApiResponse<TemplateVersionDto.Response> publishTemplate(
            @PathVariable String templateId,
            @RequestBody(required = false) TemplateWorkflowDto.PublishRequest request) {
        String tenantId = TenantContext.requireTenantId();
        Integer versionNumber = request != null ? request.getVersionNumber() : null;
        TemplateVersion version = workflowService.publishTemplate(tenantId, templateId, versionNumber);
        return ApiResponse.ok(mapVersion(version));
    }

    @PostMapping("/{templateId}/rollback/{versionNumber}")
    public ApiResponse<TemplateVersionDto.Response> rollbackTemplate(
            @PathVariable String templateId,
            @PathVariable Integer versionNumber,
            @Valid @RequestBody(required = false) TemplateVersionDto.RollbackRequest request) {
        TemplateVersionDto.RollbackRequest safeRequest = request != null ? request : new TemplateVersionDto.RollbackRequest();
        TemplateVersion version = versionService.rollbackVersion(
                templateId,
                versionNumber,
                safeRequest.getReason(),
                safeRequest.getPublish() == null || safeRequest.getPublish()
        );
        return ApiResponse.ok(mapVersion(version));
    }

    private TemplateWorkflowDto.TemplateApprovalResponse mapApproval(TemplateApproval approval) {
        TemplateWorkflowDto.TemplateApprovalResponse response = new TemplateWorkflowDto.TemplateApprovalResponse();
        response.setId(approval.getId());
        response.setTemplateId(approval.getTemplateId());
        response.setVersionNumber(approval.getVersionNumber());
        response.setRequestedBy(approval.getRequestedBy());
        response.setRequestedAt(approval.getRequestedAt() != null ? approval.getRequestedAt().toString() : null);
        response.setStatus(approval.getStatus() != null ? approval.getStatus().name() : null);
        response.setApprovedBy(approval.getApprovedBy());
        response.setApprovedAt(approval.getApprovedAt() != null ? approval.getApprovedAt().toString() : null);
        response.setRejectionReason(approval.getRejectionReason());
        response.setComments(approval.getComments());
        response.setUpdatedAt(approval.getUpdatedAt() != null ? approval.getUpdatedAt().toString() : null);
        return response;
    }

    private TemplateVersionDto.Response mapVersion(TemplateVersion version) {
        TemplateVersionDto.Response response = new TemplateVersionDto.Response();
        response.setId(version.getId());
        response.setVersionNumber(version.getVersionNumber());
        response.setSubject(version.getSubject());
        response.setHtmlContent(version.getHtmlContent());
        response.setTextContent(version.getTextContent());
        response.setChanges(version.getChanges());
        response.setIsPublished(version.getIsPublished());
        response.setCreatedAt(version.getCreatedAt() != null ? version.getCreatedAt().toString() : null);
        response.setUpdatedAt(version.getUpdatedAt() != null ? version.getUpdatedAt().toString() : null);
        return response;
    }
}
