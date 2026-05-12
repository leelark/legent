package com.legent.foundation.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.foundation.dto.DifferentiationDto;
import com.legent.foundation.service.DifferentiationPlatformService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/differentiation")
@RequiredArgsConstructor
public class DifferentiationPlatformController {

    private final DifferentiationPlatformService differentiationPlatformService;

    @PostMapping("/copilot/recommendations")
    @PreAuthorize("@rbacEvaluator.hasPermission('campaign:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createCopilotRecommendation(
            @Valid @RequestBody DifferentiationDto.CopilotRecommendationRequest request) {
        return ApiResponse.ok(differentiationPlatformService.createCopilotRecommendation(request));
    }

    @GetMapping("/copilot/recommendations")
    @PreAuthorize("@rbacEvaluator.hasPermission('campaign:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listCopilotRecommendations(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(differentiationPlatformService.listCopilotRecommendations(workspaceId, limit));
    }

    @PostMapping("/copilot/recommendations/{id}/decision")
    @PreAuthorize("@rbacEvaluator.hasPermission('campaign:*', principal.roles)")
    public ApiResponse<Map<String, Object>> decideCopilotRecommendation(
            @PathVariable String id,
            @Valid @RequestBody DifferentiationDto.CopilotDecisionRequest request) {
        return ApiResponse.ok(differentiationPlatformService.decideCopilotRecommendation(id, request));
    }

    @PostMapping("/decisioning/policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertDecisionPolicy(@Valid @RequestBody DifferentiationDto.DecisionPolicyRequest request) {
        return ApiResponse.ok(differentiationPlatformService.upsertDecisionPolicy(request));
    }

    @GetMapping("/decisioning/policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listDecisionPolicies(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(differentiationPlatformService.listDecisionPolicies(workspaceId));
    }

    @PostMapping("/decisioning/evaluate")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:*', principal.roles)")
    public ApiResponse<Map<String, Object>> evaluateDecisionPolicy(@Valid @RequestBody DifferentiationDto.DecisionEvaluateRequest request) {
        return ApiResponse.ok(differentiationPlatformService.evaluateDecisionPolicy(request));
    }

    @PostMapping("/omnichannel/flows")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertOmnichannelFlow(@Valid @RequestBody DifferentiationDto.OmnichannelFlowRequest request) {
        return ApiResponse.ok(differentiationPlatformService.upsertOmnichannelFlow(request));
    }

    @GetMapping("/omnichannel/flows")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listOmnichannelFlows(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(differentiationPlatformService.listOmnichannelFlows(workspaceId));
    }

    @PostMapping("/omnichannel/simulate")
    @PreAuthorize("@rbacEvaluator.hasPermission('workflow:*', principal.roles)")
    public ApiResponse<Map<String, Object>> simulateOmnichannelFlow(@Valid @RequestBody DifferentiationDto.OmnichannelSimulationRequest request) {
        return ApiResponse.ok(differentiationPlatformService.simulateOmnichannelFlow(request));
    }

    @PostMapping("/developer/packages")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertDeveloperPackage(@Valid @RequestBody DifferentiationDto.DeveloperPackageRequest request) {
        return ApiResponse.ok(differentiationPlatformService.upsertDeveloperPackage(request));
    }

    @GetMapping("/developer/packages")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listDeveloperPackages() {
        return ApiResponse.ok(differentiationPlatformService.listDeveloperPackages());
    }

    @PostMapping("/developer/sandboxes")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createSandbox(@Valid @RequestBody DifferentiationDto.SandboxRequest request) {
        return ApiResponse.ok(differentiationPlatformService.createSandbox(request));
    }

    @PostMapping("/developer/webhook-replays")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> createWebhookReplay(@Valid @RequestBody DifferentiationDto.WebhookReplayRequest request) {
        return ApiResponse.ok(differentiationPlatformService.createWebhookReplay(request));
    }

    @PostMapping("/ops/slo-policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> upsertSloPolicy(@Valid @RequestBody DifferentiationDto.SloPolicyRequest request) {
        return ApiResponse.ok(differentiationPlatformService.upsertSloPolicy(request));
    }

    @GetMapping("/ops/slo-policies")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:read', principal.roles)")
    public ApiResponse<List<Map<String, Object>>> listSloPolicies(@RequestParam(required = false) String workspaceId) {
        return ApiResponse.ok(differentiationPlatformService.listSloPolicies(workspaceId));
    }

    @PostMapping("/ops/slo-policies/evaluate")
    @PreAuthorize("@rbacEvaluator.hasPermission('tenant:*', principal.roles)")
    public ApiResponse<Map<String, Object>> evaluateSloPolicy(@Valid @RequestBody DifferentiationDto.SloEvaluateRequest request) {
        return ApiResponse.ok(differentiationPlatformService.evaluateSloPolicy(request));
    }
}
