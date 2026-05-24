package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.dto.performance.AiContentAssistanceEvaluateRequest;
import com.legent.foundation.dto.performance.AiContentAssistancePolicyRequest;
import com.legent.foundation.dto.performance.AiGenerationPreviewRequest;
import com.legent.foundation.dto.performance.AiProviderContractRequest;
import com.legent.foundation.dto.performance.AiProviderMeteringRequest;
import com.legent.foundation.dto.performance.ExtensionPackageRequest;
import com.legent.foundation.dto.performance.ExtensionValidationRequest;
import com.legent.foundation.dto.performance.OperationsAssistRequest;
import com.legent.foundation.dto.performance.OptimizationEvaluateRequest;
import com.legent.foundation.dto.performance.OptimizationPolicyRequest;
import com.legent.foundation.dto.performance.PersonalizationEvaluateRequest;
import com.legent.foundation.dto.performance.WorkflowBenchmarkRequest;
import com.legent.foundation.service.performance.AiContentAssistanceGovernanceService;
import com.legent.foundation.service.performance.AiGenerationPreviewService;
import com.legent.foundation.service.performance.AiProviderContractMeteringService;
import com.legent.foundation.service.performance.ClosedLoopOptimizationService;
import com.legent.foundation.service.performance.ExtensionGovernanceService;
import com.legent.foundation.service.performance.OperationsAssistanceService;
import com.legent.foundation.service.performance.PerformanceIntelligenceSummaryService;
import com.legent.foundation.service.performance.RealtimePersonalizationService;
import com.legent.foundation.service.performance.WorkflowBenchmarkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/performance-intelligence")
@RequiredArgsConstructor
public class PerformanceIntelligenceController {

    private final PerformanceIntelligenceSummaryService summaryService;
    private final RealtimePersonalizationService personalizationService;
    private final ClosedLoopOptimizationService optimizationService;
    private final ExtensionGovernanceService extensionGovernanceService;
    private final OperationsAssistanceService operationsAssistanceService;
    private final WorkflowBenchmarkService workflowBenchmarkService;
    private final AiContentAssistanceGovernanceService aiContentAssistanceGovernanceService;
    private final AiProviderContractMeteringService aiProviderContractMeteringService;
    private final AiGenerationPreviewService aiGenerationPreviewService;

    @GetMapping("/summary")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<Map<String, Object>> summary(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(summaryService.summary(workspaceId));
    }

    @PostMapping("/personalization/evaluate")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> evaluatePersonalization(@Valid @RequestBody PersonalizationEvaluateRequest request) {
        return ApiResponse.ok(personalizationService.evaluate(request));
    }

    @PostMapping("/optimization/policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertOptimizationPolicy(@Valid @RequestBody OptimizationPolicyRequest request) {
        return ApiResponse.ok(optimizationService.upsertPolicy(request));
    }

    @GetMapping("/optimization/policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listOptimizationPolicies(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(optimizationService.listPolicies(workspaceId));
    }

    @PostMapping("/optimization/evaluate")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> evaluateOptimization(@Valid @RequestBody OptimizationEvaluateRequest request) {
        return ApiResponse.ok(optimizationService.evaluate(request));
    }

    @PostMapping("/ai-content/policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertAiContentPolicy(@Valid @RequestBody AiContentAssistancePolicyRequest request) {
        return ApiResponse.ok(aiContentAssistanceGovernanceService.upsertPolicy(request));
    }

    @GetMapping("/ai-content/policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listAiContentPolicies(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(aiContentAssistanceGovernanceService.listPolicies(workspaceId));
    }

    @PostMapping("/ai-content/evaluate")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> evaluateAiContentAssistance(@Valid @RequestBody AiContentAssistanceEvaluateRequest request) {
        return ApiResponse.ok(aiContentAssistanceGovernanceService.evaluate(request));
    }

    @GetMapping("/ai-content/audits")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listAiContentAudits(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(aiContentAssistanceGovernanceService.listAudits(workspaceId, limit));
    }

    @PostMapping("/ai-provider/contracts")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertAiProviderContract(@Valid @RequestBody AiProviderContractRequest request) {
        return ApiResponse.ok(aiProviderContractMeteringService.upsertContract(request));
    }

    @GetMapping("/ai-provider/contracts")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listAiProviderContracts(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(aiProviderContractMeteringService.listContracts(workspaceId));
    }

    @PostMapping("/ai-provider/metering/evaluate")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> evaluateAiProviderMetering(@Valid @RequestBody AiProviderMeteringRequest request) {
        return ApiResponse.ok(aiProviderContractMeteringService.evaluateMetering(request));
    }

    @GetMapping("/ai-provider/metering/events")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listAiProviderMeteringEvents(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(aiProviderContractMeteringService.listMeteringEvents(workspaceId, limit));
    }

    @PostMapping("/ai-segments/preview")
    @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
    public ApiResponse<Map<String, Object>> previewAiSegmentGeneration(@Valid @RequestBody AiGenerationPreviewRequest request) {
        return ApiResponse.ok(aiGenerationPreviewService.previewForTargets(request, List.of("SEGMENT")));
    }

    @PostMapping("/ai-workflows/preview")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:write', principal.roles)")
    public ApiResponse<Map<String, Object>> previewAiWorkflowGeneration(@Valid @RequestBody AiGenerationPreviewRequest request) {
        return ApiResponse.ok(aiGenerationPreviewService.previewForTargets(request, List.of("WORKFLOW")));
    }

    @PostMapping("/extensions/packages")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertExtensionPackage(@Valid @RequestBody ExtensionPackageRequest request) {
        return ApiResponse.ok(extensionGovernanceService.upsertPackage(request));
    }

    @GetMapping("/extensions/packages")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listExtensionPackages(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(extensionGovernanceService.listPackages(workspaceId));
    }

    @PostMapping("/extensions/packages/{id}/validate")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> validateExtensionPackage(
            @PathVariable String id,
            @RequestBody(required = false) ExtensionValidationRequest request) {
        return ApiResponse.ok(extensionGovernanceService.validatePackage(id, request));
    }

    @PostMapping("/operations/assist")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> assistOperations(@Valid @RequestBody OperationsAssistRequest request) {
        return ApiResponse.ok(operationsAssistanceService.assist(request));
    }

    @GetMapping("/operations/reviews")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listOperationsReviews(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(operationsAssistanceService.listReviews(workspaceId, limit));
    }

    @PostMapping("/workflow-benchmarks")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> recordWorkflowBenchmark(@Valid @RequestBody WorkflowBenchmarkRequest request) {
        return ApiResponse.ok(workflowBenchmarkService.record(request));
    }

    @GetMapping("/workflow-benchmarks")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listWorkflowBenchmarks(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(workflowBenchmarkService.listBenchmarks(workspaceId, limit));
    }
}
