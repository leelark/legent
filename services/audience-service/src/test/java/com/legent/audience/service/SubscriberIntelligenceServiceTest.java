package com.legent.audience.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.SubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriberIntelligenceServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String EVENT_TYPE = "tracking.ingested";
    private static final String EVENT_ID = "event-1";
    private static final String IDEMPOTENCY_KEY = "idem-1";

    @Mock
    private SubscriberRepository subscriberRepository;

    @Mock
    private AudienceEventIdempotencyService idempotencyService;

    private SubscriberIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new SubscriberIntelligenceService(subscriberRepository, idempotencyService, new ObjectMapper());
    }

    @Test
    void applyTrackingIngested_InvalidPayload_PropagatesAfterIdempotencyRegistration() {
        when(idempotencyService.registerIfNew(TENANT_ID, WORKSPACE_ID, EVENT_TYPE, EVENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(true);

        assertThatThrownBy(() -> service.applyTrackingIngested(
                TENANT_ID,
                WORKSPACE_ID,
                EVENT_TYPE,
                EVENT_ID,
                IDEMPOTENCY_KEY,
                "{"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid audience intelligence payload")
                .hasCauseInstanceOf(JsonProcessingException.class);

        verify(idempotencyService).registerIfNew(TENANT_ID, WORKSPACE_ID, EVENT_TYPE, EVENT_ID, IDEMPOTENCY_KEY);
        verifyNoInteractions(subscriberRepository);
    }

    @Test
    void applyAutomationEvent_SaveFailure_PropagatesForKafkaRetry() {
        String eventType = "workflow.step.completed";
        Subscriber subscriber = new Subscriber();
        when(idempotencyService.registerIfNew(TENANT_ID, WORKSPACE_ID, eventType, EVENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(true);
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndId(TENANT_ID, WORKSPACE_ID, "sub-1"))
                .thenReturn(Optional.of(subscriber));
        when(subscriberRepository.save(subscriber))
                .thenThrow(new DataAccessResourceFailureException("audience db down"));

        assertThatThrownBy(() -> service.applyAutomationEvent(
                TENANT_ID,
                WORKSPACE_ID,
                eventType,
                EVENT_ID,
                IDEMPOTENCY_KEY,
                "{\"subscriberId\":\"sub-1\"}"))
                .isInstanceOf(DataAccessResourceFailureException.class)
                .hasMessageContaining("audience db down");

        verify(subscriberRepository).save(subscriber);
    }

    @Test
    void applyTrackingIngested_DuplicateEvent_DoesNotParseOrTouchSubscriber() {
        when(idempotencyService.registerIfNew(TENANT_ID, WORKSPACE_ID, EVENT_TYPE, EVENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(false);

        assertThatCode(() -> service.applyTrackingIngested(
                TENANT_ID,
                WORKSPACE_ID,
                EVENT_TYPE,
                EVENT_ID,
                IDEMPOTENCY_KEY,
                "{"))
                .doesNotThrowAnyException();

        verifyNoInteractions(subscriberRepository);
    }

    @Test
    void applyTrackingIngested_MissingSubscriber_NoOpsAfterValidPayload() {
        when(idempotencyService.registerIfNew(TENANT_ID, WORKSPACE_ID, EVENT_TYPE, EVENT_ID, IDEMPOTENCY_KEY))
                .thenReturn(true);
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndId(TENANT_ID, WORKSPACE_ID, "sub-1"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> service.applyTrackingIngested(
                TENANT_ID,
                WORKSPACE_ID,
                EVENT_TYPE,
                EVENT_ID,
                IDEMPOTENCY_KEY,
                "{\"subscriberId\":\"sub-1\",\"eventType\":\"OPEN\"}"))
                .doesNotThrowAnyException();

        verify(subscriberRepository).findByTenantIdAndWorkspaceIdAndId(TENANT_ID, WORKSPACE_ID, "sub-1");
        verify(subscriberRepository, never()).save(any());
    }

    @Test
    void transactionalMethods_RollBackIdempotencyForCheckedFailures() throws NoSuchMethodException {
        assertRollsBackCheckedExceptions("applyTrackingIngested");
        assertRollsBackCheckedExceptions("applyAutomationEvent");
    }

    private void assertRollsBackCheckedExceptions(String methodName) throws NoSuchMethodException {
        Method method = SubscriberIntelligenceService.class.getMethod(
                methodName,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(Arrays.asList(transactional.rollbackFor())).contains(Exception.class);
    }
}
