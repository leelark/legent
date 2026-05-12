package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.dto.GlobalEnterpriseDto;
import com.legent.foundation.service.GlobalEnterpriseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/global")
@RequiredArgsConstructor
public class GlobalEnterpriseController {

    private final GlobalEnterpriseService globalEnterpriseService;

    @PostMapping("/operating-models")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertOperatingModel(@Valid @RequestBody GlobalEnterpriseDto.OperatingModelRequest request) {
        return ApiResponse.ok(globalEnterpriseService.upsertOperatingModel(request));
    }

    @GetMapping("/operating-models")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listOperatingModels(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(globalEnterpriseService.listOperatingModels(workspaceId));
    }

    @PostMapping("/failover-drills")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createFailoverDrill(@Valid @RequestBody GlobalEnterpriseDto.FailoverDrillRequest request) {
        return ApiResponse.ok(globalEnterpriseService.createFailoverDrill(request));
    }

    @GetMapping("/failover-drills")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listFailoverDrills(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(globalEnterpriseService.listFailoverDrills(workspaceId, limit));
    }

    @PostMapping("/failover/evaluate")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> evaluateFailover(@Valid @RequestBody GlobalEnterpriseDto.FailoverEvaluationRequest request) {
        return ApiResponse.ok(globalEnterpriseService.evaluateFailover(request));
    }

    @PostMapping("/data-residency-policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertDataResidencyPolicy(@Valid @RequestBody GlobalEnterpriseDto.DataResidencyPolicyRequest request) {
        return ApiResponse.ok(globalEnterpriseService.upsertDataResidencyPolicy(request));
    }

    @GetMapping("/data-residency-policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listDataResidencyPolicies(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(globalEnterpriseService.listDataResidencyPolicies(workspaceId));
    }

    @PostMapping("/encryption-policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertEncryptionPolicy(@Valid @RequestBody GlobalEnterpriseDto.EncryptionPolicyRequest request) {
        return ApiResponse.ok(globalEnterpriseService.upsertEncryptionPolicy(request));
    }

    @GetMapping("/encryption-policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listEncryptionPolicies(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(globalEnterpriseService.listEncryptionPolicies(workspaceId));
    }

    @PostMapping("/legal-holds")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createLegalHold(@Valid @RequestBody GlobalEnterpriseDto.LegalHoldRequest request) {
        return ApiResponse.ok(globalEnterpriseService.createLegalHold(request));
    }

    @GetMapping("/legal-holds")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listLegalHolds(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(globalEnterpriseService.listLegalHolds(workspaceId, status, limit));
    }

    @PostMapping("/legal-holds/{id}/release")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> releaseLegalHold(
            @PathVariable String id,
            @Valid @RequestBody GlobalEnterpriseDto.LegalHoldReleaseRequest request) {
        return ApiResponse.ok(globalEnterpriseService.releaseLegalHold(id, request));
    }

    @PostMapping("/lineage")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> recordLineageEdge(@Valid @RequestBody GlobalEnterpriseDto.LineageEdgeRequest request) {
        return ApiResponse.ok(globalEnterpriseService.recordLineageEdge(request));
    }

    @GetMapping("/lineage")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listLineage(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(globalEnterpriseService.listLineage(workspaceId, resourceType, resourceId, limit));
    }

    @PostMapping("/policy-simulations")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> runPolicySimulation(@Valid @RequestBody GlobalEnterpriseDto.PolicySimulationRequest request) {
        return ApiResponse.ok(globalEnterpriseService.runPolicySimulation(request));
    }

    @GetMapping("/policy-simulations")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listPolicySimulations(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(globalEnterpriseService.listPolicySimulations(workspaceId, limit));
    }

    @PostMapping("/evidence-packs")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createEvidencePack(@Valid @RequestBody GlobalEnterpriseDto.EvidencePackRequest request) {
        return ApiResponse.ok(globalEnterpriseService.createEvidencePack(request));
    }

    @GetMapping("/evidence-packs")
    @PreAuthorize("@rbacEvaluator.hasPermission('audit:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listEvidencePacks(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(globalEnterpriseService.listEvidencePacks(workspaceId, limit));
    }

    @PostMapping("/marketplace/templates")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertConnectorTemplate(@Valid @RequestBody GlobalEnterpriseDto.ConnectorTemplateRequest request) {
        return ApiResponse.ok(globalEnterpriseService.upsertConnectorTemplate(request));
    }

    @PostMapping("/marketplace/templates/seed")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> seedConnectorTemplates() {
        return ApiResponse.ok(globalEnterpriseService.seedConnectorTemplates());
    }

    @GetMapping("/marketplace/templates")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listConnectorTemplates(@RequestParam(required = false) String category) {
        return ApiResponse.ok(globalEnterpriseService.listConnectorTemplates(category));
    }

    @PostMapping("/marketplace/instances")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertConnectorInstance(@Valid @RequestBody GlobalEnterpriseDto.ConnectorInstanceRequest request) {
        return ApiResponse.ok(globalEnterpriseService.upsertConnectorInstance(request));
    }

    @GetMapping("/marketplace/instances")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listConnectorInstances(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(globalEnterpriseService.listConnectorInstances(workspaceId));
    }

    @PostMapping("/marketplace/sync-jobs")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createSyncJob(@Valid @RequestBody GlobalEnterpriseDto.SyncJobRequest request) {
        return ApiResponse.ok(globalEnterpriseService.createSyncJob(request));
    }

    @GetMapping("/marketplace/sync-jobs")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listSyncJobs(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(globalEnterpriseService.listSyncJobs(workspaceId, limit));
    }

    @PostMapping("/optimization/policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertOptimizationPolicy(@Valid @RequestBody GlobalEnterpriseDto.OptimizationPolicyRequest request) {
        return ApiResponse.ok(globalEnterpriseService.upsertOptimizationPolicy(request));
    }

    @GetMapping("/optimization/policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listOptimizationPolicies(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(globalEnterpriseService.listOptimizationPolicies(workspaceId));
    }

    @PostMapping("/optimization/recommendations")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createOptimizationRecommendation(@Valid @RequestBody GlobalEnterpriseDto.OptimizationRecommendationRequest request) {
        return ApiResponse.ok(globalEnterpriseService.createOptimizationRecommendation(request));
    }

    @GetMapping("/optimization/recommendations")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listOptimizationRecommendations(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(globalEnterpriseService.listOptimizationRecommendations(workspaceId, status, limit));
    }

    @PostMapping("/optimization/recommendations/{id}/decision")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> decideOptimizationRecommendation(
            @PathVariable String id,
            @Valid @RequestBody GlobalEnterpriseDto.OptimizationDecisionRequest request) {
        return ApiResponse.ok(globalEnterpriseService.decideOptimizationRecommendation(id, request));
    }

    @PostMapping("/optimization/rollbacks")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createOptimizationRollback(@Valid @RequestBody GlobalEnterpriseDto.OptimizationRollbackRequest request) {
        return ApiResponse.ok(globalEnterpriseService.createOptimizationRollback(request));
    }

    @GetMapping("/optimization/rollbacks")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listOptimizationRollbacks(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(globalEnterpriseService.listOptimizationRollbacks(workspaceId, limit));
    }
}
