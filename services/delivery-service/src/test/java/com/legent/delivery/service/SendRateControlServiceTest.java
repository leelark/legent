package com.legent.delivery.service;

import com.legent.delivery.domain.DeliverySendReservation;
import com.legent.delivery.domain.SendRateState;
import com.legent.delivery.domain.WarmupState;
import com.legent.delivery.repository.DeliverySendReservationRepository;
import com.legent.delivery.repository.SendRateStateRepository;
import com.legent.delivery.repository.WarmupStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "legent.delivery.reservation-lease-seconds=300"
})
@Import({SendRateControlService.class, WarmupService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SendRateControlServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String SENDER_DOMAIN = "sender.example";
    private static final String PROVIDER_ID = "provider-1";
    private static final String RECIPIENT_DOMAIN = "gmail.com";

    @Autowired private SendRateControlService service;
    @Autowired private SendRateStateRepository sendRateStateRepository;
    @Autowired private WarmupStateRepository warmupStateRepository;
    @Autowired private DeliverySendReservationRepository reservationRepository;

    @BeforeEach
    void cleanDatabase() {
        reservationRepository.deleteAll();
        sendRateStateRepository.deleteAll();
        warmupStateRepository.deleteAll();
    }

    @Test
    void reserve_IsIdempotentForSameReservationId() {
        seedWarmup(20, 100, 0, 0);
        seedRateState(0);

        SendRateControlService.RateLimitDecision first = reserve("msg-1");
        SendRateControlService.RateLimitDecision second = reserve("msg-1");

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertEquals(1, currentRateState().getUsedThisMinute());
        assertEquals(1, currentWarmup().getSentThisHour());
        assertEquals(1, reservationRepository.countByTenantIdAndWorkspaceIdAndStatus(
                TENANT_ID, WORKSPACE_ID, DeliverySendReservation.STATUS_RESERVED));
    }

    @Test
    void reserve_AllowsOnlyOneConcurrentWorkerAtWarmupCap() throws Exception {
        seedWarmup(1, 1, 0, 0);
        seedRateState(0);

        CountDownLatch start = new CountDownLatch(1);
        Callable<SendRateControlService.RateLimitDecision> first = () -> {
            start.await(5, TimeUnit.SECONDS);
            return reserve("msg-1");
        };
        Callable<SendRateControlService.RateLimitDecision> second = () -> {
            start.await(5, TimeUnit.SECONDS);
            return reserve("msg-2");
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstFuture = executor.submit(first);
            var secondFuture = executor.submit(second);
            start.countDown();
            List<SendRateControlService.RateLimitDecision> decisions = List.of(
                    firstFuture.get(5, TimeUnit.SECONDS),
                    secondFuture.get(5, TimeUnit.SECONDS));

            assertEquals(1, decisions.stream().filter(SendRateControlService.RateLimitDecision::allowed).count());
            assertEquals(1, currentRateState().getUsedThisMinute());
            assertEquals(1, currentWarmup().getSentThisHour());
            assertEquals(1, reservationRepository.countByTenantIdAndWorkspaceIdAndStatus(
                    TENANT_ID, WORKSPACE_ID, DeliverySendReservation.STATUS_RESERVED));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reserve_ConcurrentSameReservationIsIdempotent() throws Exception {
        seedWarmup(1, 1, 0, 0);
        seedRateState(0);

        CountDownLatch start = new CountDownLatch(1);
        Callable<SendRateControlService.RateLimitDecision> task = () -> {
            start.await(5, TimeUnit.SECONDS);
            return reserve("msg-1");
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstFuture = executor.submit(task);
            var secondFuture = executor.submit(task);
            start.countDown();
            List<SendRateControlService.RateLimitDecision> decisions = List.of(
                    firstFuture.get(5, TimeUnit.SECONDS),
                    secondFuture.get(5, TimeUnit.SECONDS));

            assertEquals(2, decisions.stream().filter(SendRateControlService.RateLimitDecision::allowed).count());
            assertEquals(1, currentRateState().getUsedThisMinute());
            assertEquals(1, currentWarmup().getSentThisHour());
            assertEquals(1, reservationRepository.countByTenantIdAndWorkspaceIdAndStatus(
                    TENANT_ID, WORKSPACE_ID, DeliverySendReservation.STATUS_RESERVED));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void reserve_RejectionDoesNotAllocateReservationOrAdditionalWarmup() {
        seedWarmup(1, 10, 1, 1);
        seedRateState(0);

        SendRateControlService.RateLimitDecision decision = reserve("msg-denied");

        assertFalse(decision.allowed());
        assertEquals("hourly warm-up cap reached", decision.reason());
        assertEquals(0, reservationRepository.count());
        assertEquals(0, currentRateState().getUsedThisMinute());
        assertEquals(1, currentWarmup().getSentThisHour());
    }

    @Test
    void release_ReturnsTokensAndAllowsSameReservationToReserveAgain() {
        seedWarmup(1, 1, 0, 0);
        seedRateState(0);

        SendRateControlService.RateLimitDecision first = reserve("msg-1");
        service.release(TENANT_ID, WORKSPACE_ID, first.reservationId(), "provider failed");

        DeliverySendReservation released = reservationRepository
                .findByTenantIdAndWorkspaceIdAndReservationId(TENANT_ID, WORKSPACE_ID, "msg-1")
                .orElseThrow();
        assertEquals(DeliverySendReservation.STATUS_RELEASED, released.getStatus());
        assertEquals(0, currentRateState().getUsedThisMinute());
        assertEquals(0, currentWarmup().getSentThisHour());

        SendRateControlService.RateLimitDecision second = reserve("msg-1");

        assertTrue(second.allowed());
        assertEquals(1, currentRateState().getUsedThisMinute());
        assertEquals(1, currentWarmup().getSentThisHour());
        assertEquals(DeliverySendReservation.STATUS_RESERVED, reservationRepository
                .findByTenantIdAndWorkspaceIdAndReservationId(TENANT_ID, WORKSPACE_ID, "msg-1")
                .orElseThrow()
                .getStatus());
    }

    @Test
    void settleKeepsTokensConsumedAndMakesReleaseNoop() {
        seedWarmup(20, 100, 0, 0);
        seedRateState(0);

        SendRateControlService.RateLimitDecision decision = reserve("msg-1");
        service.settle(TENANT_ID, WORKSPACE_ID, decision.reservationId());
        service.release(TENANT_ID, WORKSPACE_ID, decision.reservationId(), "late release");

        DeliverySendReservation settled = reservationRepository
                .findByTenantIdAndWorkspaceIdAndReservationId(TENANT_ID, WORKSPACE_ID, "msg-1")
                .orElseThrow();
        assertEquals(DeliverySendReservation.STATUS_SETTLED, settled.getStatus());
        assertNotNull(settled.getSettledAt());
        assertEquals(1, currentRateState().getUsedThisMinute());
        assertEquals(1, currentWarmup().getSentThisHour());
    }

    @Test
    void reserve_ExpiresLeasedTokenBeforeCheckingCapacity() {
        seedWarmup(1, 1, 0, 0);
        seedRateState(0);

        SendRateControlService.RateLimitDecision first = reserve("msg-1");
        DeliverySendReservation expired = reservationRepository
                .findByTenantIdAndWorkspaceIdAndReservationId(TENANT_ID, WORKSPACE_ID, first.reservationId())
                .orElseThrow();
        expired.setLeaseExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        reservationRepository.saveAndFlush(expired);

        SendRateControlService.RateLimitDecision second = reserve("msg-2");

        assertTrue(second.allowed());
        assertEquals(1, currentRateState().getUsedThisMinute());
        assertEquals(1, currentWarmup().getSentThisHour());
        assertEquals(1, reservationRepository.countByTenantIdAndWorkspaceIdAndStatus(
                TENANT_ID, WORKSPACE_ID, DeliverySendReservation.STATUS_RESERVED));
        assertEquals(DeliverySendReservation.STATUS_RELEASED, reservationRepository
                .findByTenantIdAndWorkspaceIdAndReservationId(TENANT_ID, WORKSPACE_ID, "msg-1")
                .orElseThrow()
                .getStatus());
    }

    private SendRateControlService.RateLimitDecision reserve(String reservationId) {
        return service.reserve(
                TENANT_ID,
                WORKSPACE_ID,
                SENDER_DOMAIN,
                PROVIDER_ID,
                RECIPIENT_DOMAIN,
                10,
                0,
                reservationId);
    }

    private void seedWarmup(int hourlyLimit, int dailyLimit, int sentThisHour, int sentToday) {
        Instant now = Instant.now();
        WarmupState state = new WarmupState();
        state.setTenantId(TENANT_ID);
        state.setWorkspaceId(WORKSPACE_ID);
        state.setSenderDomain(SENDER_DOMAIN);
        state.setProviderId(PROVIDER_ID);
        state.setStage("NEW");
        state.setHourlyLimit(hourlyLimit);
        state.setDailyLimit(dailyLimit);
        state.setSentThisHour(sentThisHour);
        state.setSentToday(sentToday);
        state.setHourWindowStartedAt(now.truncatedTo(ChronoUnit.HOURS));
        state.setDayWindowStartedAt(now.truncatedTo(ChronoUnit.DAYS));
        state.setBounceRate(0.0);
        state.setComplaintRate(0.0);
        state.setNextIncreaseAt(now.plus(24, ChronoUnit.HOURS));
        warmupStateRepository.saveAndFlush(state);
    }

    private void seedRateState(int usedThisMinute) {
        Instant now = Instant.now();
        SendRateState state = new SendRateState();
        state.setTenantId(TENANT_ID);
        state.setWorkspaceId(WORKSPACE_ID);
        state.setRateLimitKey(rateLimitKey());
        state.setSenderDomain(SENDER_DOMAIN);
        state.setProviderId(PROVIDER_ID);
        state.setIspDomain(RECIPIENT_DOMAIN);
        state.setMaxPerMinute(1);
        state.setUsedThisMinute(usedThisMinute);
        state.setWindowStartedAt(now.truncatedTo(ChronoUnit.MINUTES));
        state.setThrottleState("OPEN");
        state.setRiskScore(0);
        state.setLastAdjustedAt(now);
        sendRateStateRepository.saveAndFlush(state);
    }

    private SendRateState currentRateState() {
        return sendRateStateRepository
                .findByTenantIdAndWorkspaceIdAndRateLimitKey(TENANT_ID, WORKSPACE_ID, rateLimitKey())
                .orElseThrow();
    }

    private WarmupState currentWarmup() {
        return warmupStateRepository
                .findByTenantIdAndWorkspaceIdAndSenderDomainAndProviderId(TENANT_ID, WORKSPACE_ID, SENDER_DOMAIN, PROVIDER_ID)
                .orElseThrow();
    }

    private String rateLimitKey() {
        return service.rateLimitKey(TENANT_ID, WORKSPACE_ID, SENDER_DOMAIN, PROVIDER_ID, RECIPIENT_DOMAIN);
    }
}
