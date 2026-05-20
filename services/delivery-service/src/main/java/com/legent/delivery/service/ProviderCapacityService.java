package com.legent.delivery.service;

import com.legent.delivery.domain.MessageLog;
import com.legent.delivery.domain.ProviderCapacityProfile;
import com.legent.delivery.domain.ProviderFailoverTest;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.repository.MessageLogRepository;
import com.legent.delivery.repository.ProviderCapacityProfileRepository;
import com.legent.delivery.repository.ProviderFailoverTestRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import com.legent.delivery.repository.SmtpProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProviderCapacityService {

    private final ProviderCapacityProfileRepository capacityProfileRepository;
    private final ProviderHealthStatusRepository providerHealthStatusRepository;
    private final ProviderFailoverTestRepository failoverTestRepository;
    private final MessageLogRepository messageLogRepository;
    private final DeliveryOperationsService deliveryOperationsService;
    private final SmtpProviderRepository smtpProviderRepository;

    @Transactional
    public ProviderCapacityProfile upsert(String tenantId, String workspaceId, ProviderCapacityRequest request) {
        String providerId = requireWorkspaceProvider(tenantId, workspaceId, request.providerId(), "providerId");
        String failoverProviderId = optionalWorkspaceProvider(tenantId, workspaceId, request.failoverProviderId(), "failoverProviderId");
        ProviderCapacityProfile profile = capacityProfileRepository
                .findByTenantIdAndWorkspaceIdAndProviderIdAndSenderDomainAndIspDomain(
                        tenantId,
                        workspaceId,
                        providerId,
                        normalize(request.senderDomain()),
                        normalize(request.ispDomain()))
                .orElseGet(ProviderCapacityProfile::new);
        profile.setTenantId(tenantId);
        profile.setWorkspaceId(workspaceId);
        profile.setProviderId(providerId);
        profile.setSenderDomain(normalize(request.senderDomain()));
        profile.setIspDomain(normalize(request.ispDomain()));
        profile.setHourlyCap(defaultInt(request.hourlyCap(), profile.getHourlyCap()));
        profile.setDailyCap(defaultInt(request.dailyCap(), profile.getDailyCap()));
        profile.setCurrentMaxPerMinute(defaultInt(request.currentMaxPerMinute(), profile.getCurrentMaxPerMinute()));
        profile.setMinSuccessRate(defaultDouble(request.minSuccessRate(), profile.getMinSuccessRate()));
        profile.setObservedSuccessRate(defaultDouble(request.observedSuccessRate(), profile.getObservedSuccessRate()));
        profile.setBounceRate(defaultDouble(request.bounceRate(), profile.getBounceRate()));
        profile.setComplaintRate(defaultDouble(request.complaintRate(), profile.getComplaintRate()));
        profile.setBackpressureScore(Math.max(0, Math.min(100, defaultInt(request.backpressureScore(), profile.getBackpressureScore()))));
        profile.setFailoverProviderId(failoverProviderId);
        profile.setStatus(defaultString(request.status(), profile.getStatus()).toUpperCase());
        profile.setLastEvaluatedAt(Instant.now());
        return capacityProfileRepository.save(profile);
    }

    @Transactional(readOnly = true)
    public List<ProviderCapacityProfile> list(String tenantId, String workspaceId) {
        return capacityProfileRepository.findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(tenantId, workspaceId);
    }

    @Transactional
    public ThrottleDecision evaluate(String tenantId, String workspaceId, ThrottleRequest request) {
        String providerId = requireWorkspaceProvider(tenantId, workspaceId, request.providerId(), "providerId");
        ProviderCapacityProfile profile = capacityProfileRepository
                .findByTenantIdAndWorkspaceIdAndProviderIdAndSenderDomainAndIspDomain(
                        tenantId,
                        workspaceId,
                        providerId,
                        normalize(request.senderDomain()),
                        normalize(request.ispDomain()))
                .orElseGet(() -> defaultProfile(tenantId, workspaceId, providerId, request));
        optionalWorkspaceProvider(tenantId, workspaceId, profile.getFailoverProviderId(), "failoverProviderId");
        ProviderHealthStatus health = providerHealthStatusRepository
                .findByTenantIdAndWorkspaceIdAndProviderId(tenantId, workspaceId, providerId)
                .orElse(null);
        int recommended = recommendedPerMinute(profile, health, request.riskScore() == null ? 0 : request.riskScore());
        String state = stateFor(profile, health, recommended);
        profile.setCurrentMaxPerMinute(recommended);
        profile.setLastEvaluatedAt(Instant.now());
        capacityProfileRepository.save(profile);
        return new ThrottleDecision(
                providerId,
                state,
                recommended,
                Math.max(1, recommended / 60),
                failoverProvider(profile, health, state),
                reason(profile, health, state),
                Instant.now());
    }

    @Transactional
    public ProviderFailoverTest runFailoverTest(String tenantId, String workspaceId, FailoverTestRequest request) {
        String primaryProviderId = requireWorkspaceProvider(tenantId, workspaceId, request.primaryProviderId(), "primaryProviderId");
        String failoverProviderId = optionalWorkspaceProvider(tenantId, workspaceId, request.failoverProviderId(), "failoverProviderId");
        ProviderHealthStatus primary = providerHealthStatusRepository
                .findByTenantIdAndWorkspaceIdAndProviderId(tenantId, workspaceId, primaryProviderId)
                .orElse(null);
        ProviderHealthStatus failover = failoverProviderId == null
                ? null
                : providerHealthStatusRepository
                        .findByTenantIdAndWorkspaceIdAndProviderId(tenantId, workspaceId, failoverProviderId)
                        .orElse(null);
        ProviderFailoverTest drill = new ProviderFailoverTest();
        drill.setTenantId(tenantId);
        drill.setWorkspaceId(workspaceId);
        drill.setPrimaryProviderId(primaryProviderId);
        drill.setFailoverProviderId(failoverProviderId);
        drill.setStartedAt(Instant.now());
        boolean primaryBlocked = primary == null || !primary.isHealthy();
        boolean failoverReady = failover == null || failover.isHealthy();
        drill.setStatus(primaryBlocked && failoverReady ? "PASSED" : "ATTENTION");
        drill.setResultCode(primaryBlocked ? "PRIMARY_BLOCKED" : "PRIMARY_HEALTHY");
        drill.setDiagnostic(primaryBlocked && failoverReady
                ? "Failover path accepted; replay can route away from primary provider."
                : "Failover drill needs operator review before production cutover.");
        drill.setCompletedAt(Instant.now());
        return failoverTestRepository.save(drill);
    }

    @Transactional(readOnly = true)
    public List<ProviderFailoverTest> listFailoverTests(String tenantId, String workspaceId, int limit) {
        return failoverTestRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                tenantId,
                workspaceId,
                PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> deadLetters(String tenantId, String workspaceId, int limit) {
        List<MessageLog> failed = messageLogRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                tenantId,
                workspaceId,
                PageRequest.of(0, Math.max(1, Math.min(limit, 200)))
        ).stream().filter(message -> MessageLog.DeliveryStatus.FAILED.name().equals(message.getStatus())).toList();
        Map<String, Long> byFailureClass = new LinkedHashMap<>();
        for (MessageLog message : failed) {
            String failureClass = message.getFailureClass() == null ? "UNKNOWN" : message.getFailureClass();
            byFailureClass.put(failureClass, byFailureClass.getOrDefault(failureClass, 0L) + 1L);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", failed);
        response.put("byFailureClass", byFailureClass);
        response.put("count", failed.size());
        response.put("updatedAt", Instant.now());
        return response;
    }

    @Transactional
    public Map<String, Object> replayDeadLetters(String tenantId, String workspaceId, DeadLetterReplayRequest request) {
        List<String> messageIds = request.messageIds() == null ? List.of() : request.messageIds();
        int queued = 0;
        for (String messageId : messageIds) {
            if (messageId != null && !messageId.isBlank()) {
                deliveryOperationsService.enqueueReplay(tenantId, workspaceId, messageId, defaultString(request.reason(), "DLQ_REPLAY"));
                queued++;
            }
        }
        return Map.of(
                "queued", queued,
                "requested", messageIds.size(),
                "queuedAt", Instant.now()
        );
    }

    int recommendedPerMinute(ProviderCapacityProfile profile, ProviderHealthStatus health, int riskScore) {
        int base = Math.max(1, Math.min(profile.getCurrentMaxPerMinute(), Math.max(1, profile.getHourlyCap() / 60)));
        double successFactor = clamp(profile.getObservedSuccessRate() / Math.max(0.01, profile.getMinSuccessRate()), 0.1, 1.2);
        double complaintPenalty = clamp(1.0 - (profile.getComplaintRate() * 20.0), 0.05, 1.0);
        double bouncePenalty = clamp(1.0 - (profile.getBounceRate() * 5.0), 0.05, 1.0);
        double backpressurePenalty = clamp(1.0 - (profile.getBackpressureScore() / 100.0), 0.05, 1.0);
        double riskPenalty = clamp(1.0 - (Math.max(0, Math.min(100, riskScore)) / 140.0), 0.2, 1.0);
        double healthFactor = healthFactor(health);
        return Math.max(1, (int) Math.floor(base * successFactor * complaintPenalty * bouncePenalty * backpressurePenalty * riskPenalty * healthFactor));
    }

    private ProviderCapacityProfile defaultProfile(String tenantId, String workspaceId, String providerId, ThrottleRequest request) {
        ProviderCapacityProfile profile = new ProviderCapacityProfile();
        profile.setTenantId(tenantId);
        profile.setWorkspaceId(workspaceId);
        profile.setProviderId(providerId);
        profile.setSenderDomain(normalize(request.senderDomain()));
        profile.setIspDomain(normalize(request.ispDomain()));
        profile.setHourlyCap(1000);
        profile.setDailyCap(10000);
        profile.setCurrentMaxPerMinute(60);
        return profile;
    }

    private String failoverProvider(ProviderCapacityProfile profile, ProviderHealthStatus health, String state) {
        if ("BLOCKED".equals(state) || (health != null && health.isCircuitBreakerOpen())) {
            return profile.getFailoverProviderId();
        }
        return null;
    }

    private String stateFor(ProviderCapacityProfile profile, ProviderHealthStatus health, int recommended) {
        if (!"ACTIVE".equalsIgnoreCase(profile.getStatus()) || recommended <= 1 || (health != null && health.isCircuitBreakerOpen())) {
            return "BLOCKED";
        }
        if (health != null && health.getCurrentStatus() == ProviderHealthStatus.HealthStatus.DEGRADED) {
            return "THROTTLED";
        }
        if (profile.getBackpressureScore() >= 50 || profile.getComplaintRate() >= 0.002 || profile.getBounceRate() >= 0.05) {
            return "CAUTIOUS";
        }
        return "OPEN";
    }

    private String reason(ProviderCapacityProfile profile, ProviderHealthStatus health, String state) {
        if (health != null && health.isCircuitBreakerOpen()) {
            return "provider circuit breaker open";
        }
        if (!"ACTIVE".equalsIgnoreCase(profile.getStatus())) {
            return "capacity profile inactive";
        }
        return switch (state) {
            case "BLOCKED" -> "adaptive capacity reduced to floor";
            case "THROTTLED" -> "provider health degraded";
            case "CAUTIOUS" -> "bounce, complaint, or backpressure signal elevated";
            default -> "provider capacity open";
        };
    }

    private double healthFactor(ProviderHealthStatus health) {
        if (health == null) {
            return 1.0;
        }
        if (health.isCircuitBreakerOpen() || health.getCurrentStatus() == ProviderHealthStatus.HealthStatus.UNHEALTHY) {
            return 0.05;
        }
        if (health.getCurrentStatus() == ProviderHealthStatus.HealthStatus.DEGRADED) {
            return 0.35;
        }
        return 1.0;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }

    private String requireWorkspaceProvider(String tenantId, String workspaceId, String providerId, String field) {
        String normalized = requireId(providerId, field);
        if (smtpProviderRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(normalized, tenantId, workspaceId).isEmpty()) {
            throw new IllegalArgumentException(field + " does not belong to workspace");
        }
        return normalized;
    }

    private String optionalWorkspaceProvider(String tenantId, String workspaceId, String providerId, String field) {
        String normalized = normalizeId(providerId);
        if (normalized == null) {
            return null;
        }
        return requireWorkspaceProvider(tenantId, workspaceId, normalized, field);
    }

    private String requireId(String value, String field) {
        String normalized = normalizeId(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String normalizeId(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int defaultInt(Integer value, Integer fallback) {
        return value == null ? (fallback == null ? 1 : fallback) : value;
    }

    private double defaultDouble(Double value, Double fallback) {
        return value == null ? (fallback == null ? 0.0 : fallback) : value;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record ProviderCapacityRequest(String providerId,
                                          String senderDomain,
                                          String ispDomain,
                                          Integer hourlyCap,
                                          Integer dailyCap,
                                          Integer currentMaxPerMinute,
                                          Double minSuccessRate,
                                          Double observedSuccessRate,
                                          Double bounceRate,
                                          Double complaintRate,
                                          Integer backpressureScore,
                                          String failoverProviderId,
                                          String status) {}

    public record ThrottleRequest(String providerId,
                                  String senderDomain,
                                  String ispDomain,
                                  Integer riskScore) {}

    public record ThrottleDecision(String providerId,
                                   String throttleState,
                                   int recommendedPerMinute,
                                   int recommendedPerSecond,
                                   String failoverProviderId,
                                   String reason,
                                   Instant evaluatedAt) {}

    public record FailoverTestRequest(String primaryProviderId, String failoverProviderId) {}

    public record DeadLetterReplayRequest(List<String> messageIds, String reason) {}
}
