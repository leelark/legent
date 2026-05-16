package com.legent.delivery.service;

import com.legent.common.util.IdGenerator;
import com.legent.delivery.domain.DeliverySendReservation;
import com.legent.delivery.domain.SendRateState;
import com.legent.delivery.domain.WarmupState;
import com.legent.delivery.repository.DeliverySendReservationRepository;
import com.legent.delivery.repository.SendRateStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SendRateControlService {

    private final SendRateStateRepository sendRateStateRepository;
    private final DeliverySendReservationRepository reservationRepository;
    private final WarmupService warmupService;

    @Value("${legent.delivery.reservation-lease-seconds:300}")
    private long reservationLeaseSeconds;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RateLimitDecision reserve(String tenantId,
                                     String workspaceId,
                                     String senderDomain,
                                     String providerId,
                                     String recipientDomain,
                                     Integer providerMaxSendRatePerSecond,
                                     int riskScore) {
        return reserve(
                tenantId,
                workspaceId,
                senderDomain,
                providerId,
                recipientDomain,
                providerMaxSendRatePerSecond,
                riskScore,
                UUID.randomUUID().toString());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RateLimitDecision reserve(String tenantId,
                                     String workspaceId,
                                     String senderDomain,
                                     String providerId,
                                     String recipientDomain,
                                     Integer providerMaxSendRatePerSecond,
                                     int riskScore,
                                     String reservationId) {
        String normalizedReservationId = normalizeReservationId(reservationId);
        String rateLimitKey = rateLimitKey(tenantId, workspaceId, senderDomain, providerId, recipientDomain);
        Instant now = Instant.now();

        DeliverySendReservation existing = reservationRepository
                .findActiveForUpdate(tenantId, workspaceId, normalizedReservationId)
                .orElse(null);
        if (isSettled(existing)) {
            if (!sameRateScope(existing, rateLimitKey)) {
                return rejectedForReservationScope(rateLimitKey, normalizedReservationId, now);
            }
            return existingAllowedDecision(existing, "reservation already settled");
        }
        if (isReserved(existing) && existing.getLeaseExpiresAt().isAfter(now)) {
            if (!sameRateScope(existing, rateLimitKey)) {
                return rejectedForReservationScope(rateLimitKey, normalizedReservationId, now);
            }
            return existingAllowedDecision(existing, "reservation already active");
        }

        WarmupState warmupState = warmupService.getOrCreateForUpdate(tenantId, workspaceId, senderDomain, providerId);
        SendRateState state = getOrCreateRateStateForUpdate(
                tenantId, workspaceId, rateLimitKey, senderDomain, providerId, recipientDomain);
        rollWindow(state);
        expireLeasesForCurrentRate(tenantId, workspaceId, rateLimitKey, state, warmupState, now);

        existing = reservationRepository
                .findActiveForUpdate(tenantId, workspaceId, normalizedReservationId)
                .orElse(existing);
        if (isSettled(existing)) {
            if (!sameRateScope(existing, rateLimitKey)) {
                return rejectedForReservationScope(rateLimitKey, normalizedReservationId, now);
            }
            return existingAllowedDecision(existing, "reservation already settled");
        }
        if (isReserved(existing) && existing.getLeaseExpiresAt().isAfter(now)) {
            if (!sameRateScope(existing, rateLimitKey)) {
                return rejectedForReservationScope(rateLimitKey, normalizedReservationId, now);
            }
            return existingAllowedDecision(existing, "reservation already active");
        }

        WarmupService.WarmupDecision warmup = warmupService.evaluateCapacity(warmupState, now);
        int maxPerMinute = calculateMaxPerMinute(providerMaxSendRatePerSecond, riskScore, warmupState);
        state.setMaxPerMinute(maxPerMinute);
        state.setRiskScore(riskScore);

        if (!warmup.allowed()) {
            state.setThrottleState("WARMUP_DEFER");
            sendRateStateRepository.save(state);
            return new RateLimitDecision(
                    false,
                    rateLimitKey,
                    0,
                    warmup.retryAfter(),
                    warmup.reason(),
                    state,
                    normalizedReservationId,
                    warmupState.getStage());
        }

        if (state.getUsedThisMinute() >= maxPerMinute) {
            Instant retryAfter = state.getWindowStartedAt().plus(1, ChronoUnit.MINUTES);
            state.setThrottleState("THROTTLED");
            sendRateStateRepository.save(state);
            return new RateLimitDecision(
                    false,
                    rateLimitKey,
                    maxPerMinute,
                    retryAfter,
                    "rate cap reached",
                    state,
                    normalizedReservationId,
                    warmupState.getStage());
        }

        state.setUsedThisMinute(state.getUsedThisMinute() + 1);
        state.setThrottleState(riskScore >= 40 ? "CAUTIOUS" : "OPEN");
        state.setLastAdjustedAt(now);
        warmupService.reserveLocked(warmupState);

        DeliverySendReservation reservation = existing != null ? existing : new DeliverySendReservation();
        writeReservation(
                reservation,
                tenantId,
                workspaceId,
                normalizedReservationId,
                rateLimitKey,
                senderDomain,
                providerId,
                recipientDomain,
                maxPerMinute,
                riskScore,
                state,
                warmupState,
                now);
        sendRateStateRepository.save(state);
        reservationRepository.save(reservation);
        return new RateLimitDecision(
                true,
                rateLimitKey,
                maxPerMinute,
                null,
                "capacity reserved",
                state,
                normalizedReservationId,
                warmupState.getStage());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settle(String tenantId, String workspaceId, String reservationId) {
        String normalizedReservationId = normalizeReservationId(reservationId);
        DeliverySendReservation reservation = reservationRepository
                .findActiveForUpdate(tenantId, workspaceId, normalizedReservationId)
                .orElse(null);
        if (!isReserved(reservation)) {
            return;
        }
        WarmupState warmupState = warmupService.getOrCreateForUpdate(
                tenantId, workspaceId, reservation.getSenderDomain(), reservation.getProviderId());
        warmupService.settleLocked(warmupState);
        reservation.setStatus(DeliverySendReservation.STATUS_SETTLED);
        reservation.setSettledAt(Instant.now());
        reservation.setReleaseReason(null);
        reservationRepository.save(reservation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String tenantId, String workspaceId, String reservationId, String reason) {
        String normalizedReservationId = normalizeReservationId(reservationId);
        DeliverySendReservation reservation = reservationRepository
                .findActiveForUpdate(tenantId, workspaceId, normalizedReservationId)
                .orElse(null);
        if (!isReserved(reservation)) {
            return;
        }
        WarmupState warmupState = warmupService.getOrCreateForUpdate(
                tenantId, workspaceId, reservation.getSenderDomain(), reservation.getProviderId());
        SendRateState state = getOrCreateRateStateForUpdate(
                tenantId,
                workspaceId,
                reservation.getRateLimitKey(),
                reservation.getSenderDomain(),
                reservation.getProviderId(),
                reservation.getRecipientDomain());
        rollWindow(state);
        releaseLocked(reservation, state, warmupState, Instant.now(), reason);
        sendRateStateRepository.save(state);
        reservationRepository.save(reservation);
    }

    public List<SendRateState> list(String tenantId, String workspaceId) {
        return sendRateStateRepository.findByTenantIdAndWorkspaceIdOrderByUpdatedAtDesc(tenantId, workspaceId);
    }

    private SendRateState getOrCreateRateStateForUpdate(String tenantId,
                                                        String workspaceId,
                                                        String rateLimitKey,
                                                        String senderDomain,
                                                        String providerId,
                                                        String recipientDomain) {
        SendRateState existing = sendRateStateRepository.findActiveForUpdate(tenantId, workspaceId, rateLimitKey)
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        SendRateState created = newState(tenantId, workspaceId, rateLimitKey, senderDomain, providerId, recipientDomain);
        sendRateStateRepository.insertIfAbsent(
                IdGenerator.newId(),
                tenantId,
                workspaceId,
                rateLimitKey,
                senderDomain,
                providerId,
                recipientDomain,
                created.getMaxPerMinute(),
                created.getWindowStartedAt(),
                created.getThrottleState(),
                created.getRiskScore(),
                created.getLastAdjustedAt());
        return sendRateStateRepository.findActiveForUpdate(tenantId, workspaceId, rateLimitKey)
                .orElseThrow(() -> new IllegalStateException("Unable to create send rate state"));
    }

    private void expireLeasesForCurrentRate(String tenantId,
                                            String workspaceId,
                                            String rateLimitKey,
                                            SendRateState state,
                                            WarmupState warmupState,
                                            Instant now) {
        List<DeliverySendReservation> expiredReservations = reservationRepository.findExpiredRateReservationsForUpdate(
                tenantId,
                workspaceId,
                rateLimitKey,
                DeliverySendReservation.STATUS_RESERVED,
                now);
        for (DeliverySendReservation reservation : expiredReservations) {
            releaseLocked(reservation, state, warmupState, now, "LEASE_EXPIRED");
            reservationRepository.save(reservation);
        }
    }

    private void releaseLocked(DeliverySendReservation reservation,
                               SendRateState state,
                               WarmupState warmupState,
                               Instant now,
                               String reason) {
        if (!isReserved(reservation)) {
            return;
        }
        if (sameRateScope(reservation, state.getRateLimitKey())
                && reservation.getRateWindowStartedAt() != null
                && reservation.getRateWindowStartedAt().equals(state.getWindowStartedAt())) {
            state.setUsedThisMinute(Math.max(0, state.getUsedThisMinute() - 1));
        }
        warmupService.releaseLocked(
                warmupState,
                reservation.getWarmupHourWindowStartedAt(),
                reservation.getWarmupDayWindowStartedAt());
        reservation.setStatus(DeliverySendReservation.STATUS_RELEASED);
        reservation.setReleasedAt(now);
        reservation.setLeaseExpiresAt(now);
        reservation.setReleaseReason(reason == null || reason.isBlank() ? "RELEASED" : reason);
    }

    private void writeReservation(DeliverySendReservation reservation,
                                  String tenantId,
                                  String workspaceId,
                                  String reservationId,
                                  String rateLimitKey,
                                  String senderDomain,
                                  String providerId,
                                  String recipientDomain,
                                  int maxPerMinute,
                                  int riskScore,
                                  SendRateState state,
                                  WarmupState warmupState,
                                  Instant now) {
        reservation.setTenantId(tenantId);
        reservation.setWorkspaceId(workspaceId);
        reservation.setReservationId(reservationId);
        reservation.setRateLimitKey(rateLimitKey);
        reservation.setSenderDomain(normalizeRequired(senderDomain, "senderDomain"));
        reservation.setProviderId(normalizeRequired(providerId, "providerId"));
        reservation.setRecipientDomain(normalizeNullable(recipientDomain));
        reservation.setStatus(DeliverySendReservation.STATUS_RESERVED);
        reservation.setMaxPerMinute(maxPerMinute);
        reservation.setRiskScore(riskScore);
        reservation.setWarmupHourlyLimit(warmupState.getHourlyLimit());
        reservation.setWarmupDailyLimit(warmupState.getDailyLimit());
        reservation.setRateWindowStartedAt(state.getWindowStartedAt());
        reservation.setWarmupHourWindowStartedAt(warmupState.getHourWindowStartedAt());
        reservation.setWarmupDayWindowStartedAt(warmupState.getDayWindowStartedAt());
        reservation.setLeaseExpiresAt(now.plus(Math.max(30, reservationLeaseSeconds), ChronoUnit.SECONDS));
        reservation.setReservedAt(now);
        reservation.setSettledAt(null);
        reservation.setReleasedAt(null);
        reservation.setReleaseReason(null);
    }

    private RateLimitDecision existingAllowedDecision(DeliverySendReservation reservation, String reason) {
        return new RateLimitDecision(
                true,
                reservation.getRateLimitKey(),
                reservation.getMaxPerMinute(),
                null,
                reason,
                null,
                reservation.getReservationId(),
                null);
    }

    private RateLimitDecision rejectedForReservationScope(String rateLimitKey, String reservationId, Instant now) {
        return new RateLimitDecision(
                false,
                rateLimitKey,
                0,
                now.plus(1, ChronoUnit.MINUTES),
                "reservation id already belongs to a different send scope",
                null,
                reservationId,
                null);
    }

    private boolean isReserved(DeliverySendReservation reservation) {
        return reservation != null && DeliverySendReservation.STATUS_RESERVED.equals(reservation.getStatus());
    }

    private boolean isSettled(DeliverySendReservation reservation) {
        return reservation != null && DeliverySendReservation.STATUS_SETTLED.equals(reservation.getStatus());
    }

    private boolean sameRateScope(DeliverySendReservation reservation, String rateLimitKey) {
        return reservation != null && rateLimitKey.equals(reservation.getRateLimitKey());
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

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String normalizeRequired(String value, String field) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private String normalizeReservationId(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return reservationId.trim();
    }

    public record RateLimitDecision(boolean allowed,
                                    String rateLimitKey,
                                    int maxPerMinute,
                                    Instant retryAfter,
                                    String reason,
                                    SendRateState state,
                                    String reservationId,
                                    String warmupStage) {
        public RateLimitDecision(boolean allowed,
                                 String rateLimitKey,
                                 int maxPerMinute,
                                 Instant retryAfter,
                                 String reason,
                                 SendRateState state) {
            this(allowed, rateLimitKey, maxPerMinute, retryAfter, reason, state, null, null);
        }
    }
}
