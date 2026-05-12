package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.dto.performance.ExtensionPackageRequest;
import com.legent.foundation.dto.performance.ExtensionValidationRequest;
import com.legent.foundation.dto.performance.OperationsAssistRequest;
import com.legent.foundation.dto.performance.OptimizationEvaluateRequest;
import com.legent.foundation.dto.performance.OptimizationPolicyRequest;
import com.legent.foundation.dto.performance.PersonalizationEvaluateRequest;
import com.legent.foundation.dto.performance.WorkflowBenchmarkRequest;
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
