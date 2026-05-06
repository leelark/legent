package com.legent.delivery.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.delivery.domain.DeliveryReplayQueue;
import com.legent.delivery.domain.InboxSafetyEvaluation;
import com.legent.delivery.domain.MessageLog;
import com.legent.delivery.domain.ProviderDecisionTrace;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.domain.SendRateState;
import com.legent.delivery.domain.WarmupState;
import com.legent.delivery.repository.InboxSafetyEvaluationRepository;
import com.legent.delivery.repository.ProviderDecisionTraceRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import com.legent.delivery.repository.SmtpProviderRepository;
import com.legent.delivery.service.DeliveryOperationsService;
import com.legent.delivery.service.InboxSafetyService;
import com.legent.delivery.service.SendRateControlService;
import com.legent.delivery.service.WarmupService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
public class DeliveryOperationsController {

    private final DeliveryOperationsService operationsService;
    private final ProviderHealthStatusRepository providerHealthStatusRepository;
    private final SmtpProviderRepository smtpProviderRepository;
    private final InboxSafetyService inboxSafetyService;
    private final SendRateControlService sendRateControlService;
    private final WarmupService warmupService;
    private final ProviderDecisionTraceRepository providerDecisionTraceRepository;
    private final InboxSafetyEvaluationRepository inboxSafetyEvaluationRepository;

    @GetMapping("/queue/stats")
    public ApiResponse<Map<String, Object>> queueStats() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(operationsService.queueStats(tenantId, workspaceId));
    }

    @GetMapping("/messages")
    public ApiResponse<List<MessageLog>> messages(@RequestParam(defaultValue = "50") int limit) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(operationsService.recentMessages(tenantId, workspaceId, limit));
    }

    @PostMapping("/messages/{messageId}/retry")
    public ApiResponse<MessageLog> retryMessage(@PathVariable String messageId,
                                                @RequestBody(required = false) RetryRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        String reason = request != null ? request.reason() : null;
        return ApiResponse.ok(operationsService.retryMessage(tenantId, workspaceId, messageId, reason));
    }

    @PostMapping("/replay")
    public ApiResponse<DeliveryReplayQueue> enqueueReplay(@RequestBody ReplayRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        if (request == null || request.messageId() == null || request.messageId().isBlank()) {
            throw new IllegalArgumentException("messageId is required");
        }
        return ApiResponse.ok(operationsService.enqueueReplay(tenantId, workspaceId, request.messageId(), request.reason()));
    }

    @PostMapping("/replay/process")
    public ApiResponse<Map<String, Object>> processReplay(@RequestBody(required = false) ReplayProcessRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        int maxItems = request != null && request.maxItems() != null ? request.maxItems() : 100;
        int processed = operationsService.processReplayQueue(tenantId, workspaceId, maxItems);
        return ApiResponse.ok(Map.of(
                "processed", processed,
                "processedAt", Instant.now()
        ));
    }

    @GetMapping("/diagnostics/failures")
    public ApiResponse<Map<String, Object>> failureDiagnostics() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(operationsService.failureDiagnostics(tenantId, workspaceId));
    }

    @PostMapping("/safety/evaluate")
    public ApiResponse<InboxSafetyService.InboxSafetyResult> evaluateSafety(@RequestBody SafetyEvaluateRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        InboxSafetyService.InboxSafetyResult result = inboxSafetyService.evaluate(new InboxSafetyService.InboxSafetyRequest(
                tenantId,
                workspaceId,
                request.campaignId(),
                request.jobId(),
                request.batchId(),
                request.messageId(),
                request.subscriberId(),
                request.email(),
                request.fromEmail(),
                request.senderDomain(),
                request.providerId(),
                request.subject(),
                request.htmlBody(),
                request.engagementScore(),
                request.attemptCount(),
                request.rateLimitKey(),
                request.warmupStage()));
        return ApiResponse.ok(result);
    }

    @GetMapping("/safety/evaluations")
    public ApiResponse<List<InboxSafetyEvaluation>> safetyEvaluations(@RequestParam(defaultValue = "50") int limit) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(inboxSafetyEvaluationRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                tenantId, workspaceId, PageRequest.of(0, Math.max(1, Math.min(limit, 200)))));
    }

    @GetMapping("/rate-limits")
    public ApiResponse<List<SendRateState>> rateLimits() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(sendRateControlService.list(tenantId, workspaceId));
    }

    @GetMapping("/warmup/status")
    public ApiResponse<Map<String, Object>> warmupStatus() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        long activeProviders = smtpProviderRepository.findByTenantIdAndIsActiveTrueOrderByPriorityAsc(tenantId).size();
        List<ProviderHealthStatus> health = providerHealthStatusRepository.findByTenantId(tenantId).stream()
                .filter(status -> workspaceId.equals(status.getWorkspaceId()))
                .toList();
        long healthy = health.stream().filter(status -> status.getCurrentStatus() == ProviderHealthStatus.HealthStatus.HEALTHY).count();
        long degraded = health.stream().filter(status -> status.getCurrentStatus() == ProviderHealthStatus.HealthStatus.DEGRADED).count();
        long unhealthy = health.stream().filter(status -> status.getCurrentStatus() == ProviderHealthStatus.HealthStatus.UNHEALTHY).count();
        Map<String, Object> warmup = new HashMap<>();
        warmup.put("activeProviders", activeProviders);
        warmup.put("healthyProviders", healthy);
        warmup.put("degradedProviders", degraded);
        warmup.put("unhealthyProviders", unhealthy);
        List<WarmupState> warmupStates = warmupService.list(tenantId, workspaceId);
        warmup.put("states", warmupStates);
        warmup.put("rampStage", warmupStates.stream().findFirst().map(WarmupState::getStage).orElse("NOT_STARTED"));
        warmup.put("allowedVolume", warmupStates.stream().mapToInt(state -> state.getHourlyLimit() != null ? state.getHourlyLimit() : 0).sum());
        warmup.put("nextIncreaseTime", warmupStates.stream()
                .map(WarmupState::getNextIncreaseAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null));
        warmup.put("rollbackReason", warmupStates.stream()
                .map(WarmupState::getRollbackReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .findFirst()
                .orElse(null));
        warmup.put("readiness", activeProviders == 0 ? 0 : Math.max(0, (int) Math.round((healthy * 100.0) / activeProviders)));
        warmup.put("updatedAt", Instant.now());
        return ApiResponse.ok(warmup);
    }

    @GetMapping("/routing/decision-trace")
    public ApiResponse<List<ProviderDecisionTrace>> routingDecisionTrace(@RequestParam(defaultValue = "50") int limit) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(providerDecisionTraceRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                tenantId, workspaceId, PageRequest.of(0, Math.max(1, Math.min(limit, 200)))));
    }

    public record RetryRequest(String reason) {}
    public record ReplayRequest(String messageId, String reason) {}
    public record ReplayProcessRequest(Integer maxItems) {}
    public record SafetyEvaluateRequest(String campaignId,
                                        String jobId,
                                        String batchId,
                                        String messageId,
                                        String subscriberId,
                                        String email,
                                        String fromEmail,
                                        String senderDomain,
                                        String providerId,
                                        String subject,
                                        String htmlBody,
                                        Integer engagementScore,
                                        Integer attemptCount,
                                        String rateLimitKey,
                                        String warmupStage) {}
}
