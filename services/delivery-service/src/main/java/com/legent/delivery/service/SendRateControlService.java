package com.legent.delivery.service;

import com.legent.delivery.domain.SendRateState;
import com.legent.delivery.domain.WarmupState;
import com.legent.delivery.repository.SendRateStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SendRateControlService {

    private final SendRateStateRepository sendRateStateRepository;
    private final WarmupService warmupService;

    @Transactional
    public RateLimitDecision reserve(String tenantId,
                                     String workspaceId,
                                     String senderDomain,
                                     String providerId,
                                     String recipientDomain,
                                     Integer providerMaxSendRatePerSecond,
                                     int riskScore) {
        String rateLimitKey = rateLimitKey(tenantId, workspaceId, senderDomain, providerId, recipientDomain);
        SendRateState state = sendRateStateRepository.findByTenantIdAndWorkspaceIdAndRateLimitKey(tenantId, workspaceId, rateLimitKey)
                .map(this::rollWindow)
                .orElseGet(() -> newState(tenantId, workspaceId, rateLimitKey, senderDomain, providerId, recipientDomain));

        WarmupService.WarmupDecision warmup = warmupService.reserve(tenantId, workspaceId, senderDomain, providerId);
        int maxPerMinute = calculateMaxPerMinute(providerMaxSendRatePerSecond, riskScore, warmup.state());
        state.setMaxPerMinute(maxPerMinute);
        state.setRiskScore(riskScore);

        if (!warmup.allowed()) {
            state.setThrottleState("WARMUP_DEFER");
            sendRateStateRepository.save(state);
            return new RateLimitDecision(false, rateLimitKey, 0, warmup.retryAfter(), warmup.reason(), state);
        }

        if (state.getUsedThisMinute() >= maxPerMinute) {
            Instant retryAfter = state.getWindowStartedAt().plus(1, ChronoUnit.MINUTES);
            state.setThrottleState("THROTTLED");
            sendRateStateRepository.save(state);
            return new RateLimitDecision(false, rateLimitKey, maxPerMinute, retryAfter, "rate cap reached", state);
        }

        state.setUsedThisMinute(state.getUsedThisMinute() + 1);
        state.setThrottleState(riskScore >= 40 ? "CAUTIOUS" : "OPEN");
        state.setLastAdjustedAt(Instant.now());
        sendRateStateRepository.save(state);
        return new RateLimitDecision(true, rateLimitKey, maxPerMinute, null, "capacity reserved", state);
    }

    public List<SendRateState> list(String tenantId, String workspaceId) {
        return sendRateStateRepository.findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(tenantId, workspaceId);
    }

    private int calculateMaxPerMinute(Integer providerMaxSendRatePerSecond, int riskScore, WarmupState warmupState) {
        int providerCap = providerMaxSendRatePerSecond != null && providerMaxSendRatePerSecond > 0
                ? Math.max(1, providerMaxSendRatePerSecond * 60)
                : 1000;
        int riskCap = switch (riskScore / 10) {
            case 0, 1 -> 1000;
            case 2, 3 -> 500;
            case 4, 5 -> 120;
            default -> 30;
        };
        int warmupCap = warmupState != null && warmupState.getHourlyLimit() != null
                ? Math.max(1, warmupState.getHourlyLimit() / 60)
                : 1000;
        return Math.max(1, Math.min(providerCap, Math.min(riskCap, warmupCap)));
    }

    private SendRateState rollWindow(SendRateState state) {
        Instant now = Instant.now();
        if (state.getWindowStartedAt().plus(1, ChronoUnit.MINUTES).isBefore(now)) {
            state.setWindowStartedAt(now.truncatedTo(ChronoUnit.MINUTES));
            state.setUsedThisMinute(0);
        }
        return state;
    }

    private SendRateState newState(String tenantId,
                                   String workspaceId,
                                   String rateLimitKey,
                                   String senderDomain,
                                   String providerId,
                                   String recipientDomain) {
        Instant now = Instant.now();
        SendRateState state = new SendRateState();
        state.setTenantId(tenantId);
        state.setWorkspaceId(workspaceId);
        state.setRateLimitKey(rateLimitKey);
        state.setSenderDomain(senderDomain);
        state.setProviderId(providerId);
        state.setIspDomain(recipientDomain);
        state.setMaxPerMinute(1);
        state.setUsedThisMinute(0);
        state.setWindowStartedAt(now.truncatedTo(ChronoUnit.MINUTES));
        state.setThrottleState("OPEN");
        state.setRiskScore(0);
        state.setLastAdjustedAt(now);
        return state;
    }

    public String rateLimitKey(String tenantId, String workspaceId, String senderDomain, String providerId, String recipientDomain) {
        return normalize(tenantId) + ":" + normalize(workspaceId) + ":" + normalize(senderDomain)
                + ":" + normalize(providerId) + ":" + normalize(recipientDomain);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.trim().toLowerCase();
    }

    public record RateLimitDecision(boolean allowed,
                                    String rateLimitKey,
                                    int maxPerMinute,
                                    Instant retryAfter,
                                    String reason,
                                    SendRateState state) {}
}
