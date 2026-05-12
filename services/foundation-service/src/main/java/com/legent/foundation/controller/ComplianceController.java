package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.dto.ComplianceDto;
import com.legent.foundation.service.ComplianceEvidenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceEvidenceService complianceEvidenceService;

    @PostMapping("/audit-evidence")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> recordAuditEvidence(@Valid @RequestBody ComplianceDto.AuditEvidenceRequest request) {
        return ApiResponse.ok(complianceEvidenceService.recordAuditEvidence(request));
    }

    @GetMapping("/audit-evidence")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listAuditEvidence(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(complianceEvidenceService.listAuditEvidence(workspaceId, resourceType, limit));
    }

    @PostMapping("/retention-matrix")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertRetentionPolicy(@Valid @RequestBody ComplianceDto.RetentionPolicyRequest request) {
        return ApiResponse.ok(complianceEvidenceService.upsertRetentionPolicy(request));
    }

    @GetMapping("/retention-matrix")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listRetentionMatrix(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(complianceEvidenceService.listRetentionMatrix(workspaceId));
    }

    @PostMapping("/consent-ledger")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> recordConsent(@Valid @RequestBody ComplianceDto.ConsentLedgerRequest request) {
        return ApiResponse.ok(complianceEvidenceService.recordConsent(request));
    }

    @GetMapping("/consent-ledger")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listConsentLedger(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String subjectId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(complianceEvidenceService.listConsentLedger(workspaceId, subjectId, limit));
    }

    @PostMapping("/privacy-requests")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createPrivacyRequest(@Valid @RequestBody ComplianceDto.PrivacyRequest request) {
        return ApiResponse.ok(complianceEvidenceService.createPrivacyRequest(request));
    }

    @PostMapping("/privacy-requests/{id}/status")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> updatePrivacyRequest(
            @PathVariable String id,
            @Valid @RequestBody ComplianceDto.PrivacyStatusRequest request) {
        return ApiResponse.ok(complianceEvidenceService.updatePrivacyRequest(id, request));
    }

    @GetMapping("/privacy-requests")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listPrivacyRequests(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(complianceEvidenceService.listPrivacyRequests(workspaceId, status, limit));
    }

    @PostMapping("/exports")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createComplianceExport(@Valid @RequestBody ComplianceDto.ComplianceExportRequest request) {
        return ApiResponse.ok(complianceEvidenceService.createComplianceExport(request));
    }

    @GetMapping("/exports")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listComplianceExports(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(complianceEvidenceService.listComplianceExports(workspaceId, limit));
    }
}
