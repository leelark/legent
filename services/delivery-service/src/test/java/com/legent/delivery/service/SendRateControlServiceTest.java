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
    @Autowired private WarmupService warmupService;
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

    @Test
    void list_usesBoundedFirstPageAndPreservesScope() {
        for (int index = 0; index < 4; index++) {
            seedRateState(TENANT_ID, WORKSPACE_ID, "sender-" + index + ".example", "provider-" + index, RECIPIENT_DOMAIN);
        }
        seedRateState(TENANT_ID, "workspace-other", "other.example", PROVIDER_ID, RECIPIENT_DOMAIN);
        seedRateState("tenant-other", WORKSPACE_ID, "other-tenant.example", PROVIDER_ID, RECIPIENT_DOMAIN);

        List<SendRateState> states = service.list(TENANT_ID, WORKSPACE_ID, 2);

        assertEquals(2, states.size());
        assertTrue(states.stream().allMatch(state -> TENANT_ID.equals(state.getTenantId())));
        assertTrue(states.stream().allMatch(state -> WORKSPACE_ID.equals(state.getWorkspaceId())));
    }

    @Test
    void warmupList_usesBoundedFirstPageAndPreservesScope() {
        for (int index = 0; index < 4; index++) {
            seedWarmupState(TENANT_ID, WORKSPACE_ID, "sender-" + index + ".example", "provider-" + index, 20, 100, 0, 0);
        }
        seedWarmupState(TENANT_ID, "workspace-other", "other.example", PROVIDER_ID, 20, 100, 0, 0);
        seedWarmupState("tenant-other", WORKSPACE_ID, "other-tenant.example", PROVIDER_ID, 20, 100, 0, 0);

        List<WarmupState> states = warmupService.list(TENANT_ID, WORKSPACE_ID, 2);

        assertEquals(2, states.size());
        assertTrue(states.stream().allMatch(state -> TENANT_ID.equals(state.getTenantId())));
        assertTrue(states.stream().allMatch(state -> WORKSPACE_ID.equals(state.getWorkspaceId())));
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
        seedWarmupState(TENANT_ID, WORKSPACE_ID, SENDER_DOMAIN, PROVIDER_ID, hourlyLimit, dailyLimit, sentThisHour, sentToday);
    }

    private void seedWarmupState(String tenantId,
                                 String workspaceId,
                                 String senderDomain,
                                 String providerId,
                                 int hourlyLimit,
                                 int dailyLimit,
                                 int sentThisHour,
                                 int sentToday) {
        Instant now = Instant.now();
        WarmupState state = new WarmupState();
        state.setTenantId(tenantId);
        state.setWorkspaceId(workspaceId);
        state.setSenderDomain(senderDomain);
        state.setProviderId(providerId);
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
        seedRateState(TENANT_ID, WORKSPACE_ID, SENDER_DOMAIN, PROVIDER_ID, RECIPIENT_DOMAIN, usedThisMinute);
    }

    private void seedRateState(String tenantId,
                               String workspaceId,
                               String senderDomain,
                               String providerId,
                               String recipientDomain) {
        seedRateState(tenantId, workspaceId, senderDomain, providerId, recipientDomain, 0);
    }

    private void seedRateState(String tenantId,
                               String workspaceId,
                               String senderDomain,
                               String providerId,
                               String recipientDomain,
                               int usedThisMinute) {
        Instant now = Instant.now();
        SendRateState state = new SendRateState();
        state.setTenantId(tenantId);
        state.setWorkspaceId(workspaceId);
        state.setRateLimitKey(service.rateLimitKey(tenantId, workspaceId, senderDomain, providerId, recipientDomain));
        state.setSenderDomain(senderDomain);
        state.setProviderId(providerId);
        state.setIspDomain(recipientDomain);
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
