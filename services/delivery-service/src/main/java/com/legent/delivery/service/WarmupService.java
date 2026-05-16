package com.legent.delivery.service;

import com.legent.common.util.IdGenerator;
import com.legent.delivery.domain.WarmupState;
import com.legent.delivery.repository.WarmupStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WarmupService {

    private final WarmupStateRepository warmupStateRepository;

    @Transactional
    public WarmupState getOrCreate(String tenantId, String workspaceId, String senderDomain, String providerId) {
        String normalizedDomain = normalize(senderDomain);
        String normalizedProvider = normalize(providerId);
        if (normalizedDomain == null || normalizedProvider == null) {
            throw new IllegalArgumentException("senderDomain and providerId are required for warm-up");
        }
        return warmupStateRepository.findByTenantIdAndWorkspaceIdAndSenderDomainAndProviderId(
                        tenantId, workspaceId, normalizedDomain, normalizedProvider)
                .map(this::rollWindows)
                .orElseGet(() -> warmupStateRepository.save(newState(tenantId, workspaceId, normalizedDomain, normalizedProvider)));
    }

    @Transactional
    public WarmupDecision reserve(String tenantId, String workspaceId, String senderDomain, String providerId) {
        WarmupState state = getOrCreateForUpdate(tenantId, workspaceId, senderDomain, providerId);
        WarmupDecision decision = evaluateCapacity(state, Instant.now());
        if (decision.allowed()) {
            reserveLocked(state);
            warmupStateRepository.save(state);
        }
        return decision;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public WarmupState getOrCreateForUpdate(String tenantId, String workspaceId, String senderDomain, String providerId) {
        String normalizedDomain = normalize(senderDomain);
        String normalizedProvider = normalize(providerId);
        if (normalizedDomain == null || normalizedProvider == null) {
            throw new IllegalArgumentException("senderDomain and providerId are required for warm-up");
        }
        WarmupState existing = warmupStateRepository
                .findActiveForUpdate(tenantId, workspaceId, normalizedDomain, normalizedProvider)
                .map(this::rollWindows)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        WarmupState created = newState(tenantId, workspaceId, normalizedDomain, normalizedProvider);
        warmupStateRepository.insertIfAbsent(
                IdGenerator.newId(),
                tenantId,
                workspaceId,
                normalizedDomain,
                normalizedProvider,
                created.getStage(),
                created.getHourlyLimit(),
                created.getDailyLimit(),
                created.getHourWindowStartedAt(),
                created.getDayWindowStartedAt(),
                created.getNextIncreaseAt());
        return warmupStateRepository.findActiveForUpdate(tenantId, workspaceId, normalizedDomain, normalizedProvider)
                .map(this::rollWindows)
                .orElseThrow(() -> new IllegalStateException("Unable to create warm-up state"));
    }

    public WarmupDecision evaluateCapacity(WarmupState state, Instant now) {
        if (state.getRollbackReason() != null && !state.getRollbackReason().isBlank()) {
            return new WarmupDecision(false, state, now.plus(1, ChronoUnit.HOURS), state.getRollbackReason());
        }
        if (state.getSentToday() >= state.getDailyLimit()) {
            return new WarmupDecision(false, state, state.getDayWindowStartedAt().plus(1, ChronoUnit.DAYS), "daily warm-up cap reached");
        }
        if (state.getSentThisHour() >= state.getHourlyLimit()) {
            return new WarmupDecision(false, state, state.getHourWindowStartedAt().plus(1, ChronoUnit.HOURS), "hourly warm-up cap reached");
        }
        return new WarmupDecision(true, state, null, "warm-up capacity available");
    }

    public void reserveLocked(WarmupState state) {
        state.setSentThisHour(state.getSentThisHour() + 1);
        state.setSentToday(state.getSentToday() + 1);
    }

    public void releaseLocked(WarmupState state, Instant hourWindowStartedAt, Instant dayWindowStartedAt) {
        if (hourWindowStartedAt != null && hourWindowStartedAt.equals(state.getHourWindowStartedAt())) {
            state.setSentThisHour(Math.max(0, state.getSentThisHour() - 1));
        }
        if (dayWindowStartedAt != null && dayWindowStartedAt.equals(state.getDayWindowStartedAt())) {
            state.setSentToday(Math.max(0, state.getSentToday() - 1));
        }
    }

    public void settleLocked(WarmupState state) {
        maybeAdvance(state);
    }

    @Transactional
    public void recordSent(String tenantId, String workspaceId, String senderDomain, String providerId) {
        WarmupState state = getOrCreate(tenantId, workspaceId, senderDomain, providerId);
        state.setSentThisHour(state.getSentThisHour() + 1);
        state.setSentToday(state.getSentToday() + 1);
        maybeAdvance(state);
        warmupStateRepository.save(state);
    }

    @Transactional
    public void recordNegativeSignal(String tenantId, String workspaceId, String senderDomain, String providerId, String failureClass) {
        WarmupState state = getOrCreate(tenantId, workspaceId, senderDomain, providerId);
        if ("COMPLAINT".equals(failureClass) || "AUTH_REJECTION".equals(failureClass) || "REPUTATION_BLOCKED".equals(failureClass)) {
            state.setRollbackReason("warm-up rollback: " + failureClass);
            state.setStage("ROLLED_BACK");
            state.setHourlyLimit(Math.max(5, state.getHourlyLimit() / 2));
            state.setDailyLimit(Math.max(25, state.getDailyLimit() / 2));
            state.setNextIncreaseAt(Instant.now().plus(24, ChronoUnit.HOURS));
        }
        warmupStateRepository.save(state);
    }

    public List<WarmupState> list(String tenantId, String workspaceId) {
        return warmupStateRepository.findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(tenantId, workspaceId);
    }

    private WarmupState newState(String tenantId, String workspaceId, String senderDomain, String providerId) {
        Instant now = Instant.now();
        WarmupState state = new WarmupState();
        state.setTenantId(tenantId);
        state.setWorkspaceId(workspaceId);
        state.setSenderDomain(senderDomain);
        state.setProviderId(providerId);
        state.setStage("NEW");
        state.setHourlyLimit(20);
        state.setDailyLimit(100);
        state.setSentThisHour(0);
        state.setSentToday(0);
        state.setHourWindowStartedAt(now.truncatedTo(ChronoUnit.HOURS));
        state.setDayWindowStartedAt(now.truncatedTo(ChronoUnit.DAYS));
        state.setBounceRate(0.0);
        state.setComplaintRate(0.0);
        state.setNextIncreaseAt(now.plus(24, ChronoUnit.HOURS));
        return state;
    }

    private WarmupState rollWindows(WarmupState state) {
        Instant now = Instant.now();
        if (state.getHourWindowStartedAt().plus(1, ChronoUnit.HOURS).isBefore(now)) {
            state.setHourWindowStartedAt(now.truncatedTo(ChronoUnit.HOURS));
            state.setSentThisHour(0);
        }
        if (state.getDayWindowStartedAt().plus(1, ChronoUnit.DAYS).isBefore(now)) {
            state.setDayWindowStartedAt(now.truncatedTo(ChronoUnit.DAYS));
            state.setSentToday(0);
        }
        return state;
    }

    private void maybeAdvance(WarmupState state) {
        Instant now = Instant.now();
        if (state.getNextIncreaseAt().isAfter(now) || state.getRollbackReason() != null) {
            return;
        }
        state.setHourlyLimit(Math.min(60000, (int) Math.ceil(state.getHourlyLimit() * 1.35)));
        state.setDailyLimit(Math.min(500000, (int) Math.ceil(state.getDailyLimit() * 1.35)));
        state.setStage(state.getHourlyLimit() >= 500 ? "TRUSTED" : "RAMPING");
        state.setNextIncreaseAt(now.plus(24, ChronoUnit.HOURS));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }

    public record WarmupDecision(boolean allowed, WarmupState state, Instant retryAfter, String reason) {}
}
