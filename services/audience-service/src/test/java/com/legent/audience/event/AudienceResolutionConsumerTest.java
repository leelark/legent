package com.legent.audience.event;

import com.legent.audience.client.DeliverabilityServiceClient;
import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.AudienceCandidateRepository;
import com.legent.audience.repository.AudienceCandidateRepository.AudienceCandidateCriteria;
import com.legent.audience.service.AudienceEventIdempotencyService;
import com.legent.audience.service.SendEligibilityService;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudienceResolutionConsumerTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String CAMPAIGN_ID = "campaign-1";
    private static final String JOB_ID = "job-1";

    @Mock private EventPublisher eventPublisher;
    @Mock private AudienceCandidateRepository audienceCandidateRepository;
    @Mock private DeliverabilityServiceClient deliverabilityClient;
    @Mock private AudienceEventIdempotencyService idempotencyService;
    @Mock private SendEligibilityService sendEligibilityService;

    private AudienceResolutionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AudienceResolutionConsumer(
                eventPublisher,
                audienceCandidateRepository,
                deliverabilityClient,
                idempotencyService,
                sendEligibilityService);
        lenient().when(eventPublisher.publish(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publishesResolvedAudienceInBoundedChunksWithLastChunkOnlyOnFinalEvent() {
        int totalSubscribers = AppConstants.SEND_BATCH_SIZE + 1;
        List<Subscriber> subscribers = IntStream.range(0, totalSubscribers)
                .mapToObj(this::subscriber)
                .toList();

        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-1"), eq("idem-1")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(subscribers.subList(0, AppConstants.SEND_BATCH_SIZE));
        when(audienceCandidateRepository.findNextCandidates(
                eq(TENANT_ID),
                eq(WORKSPACE_ID),
                any(AudienceCandidateCriteria.class),
                eq(subscribers.get(AppConstants.SEND_BATCH_SIZE - 1).getId()),
                eq(resolutionPage())))
                .thenReturn(subscribers.subList(AppConstants.SEND_BATCH_SIZE, totalSubscribers));
        when(deliverabilityClient.checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList()))
                .thenReturn(Set.of());
        when(sendEligibilityService.isSendEligible(any(Subscriber.class))).thenReturn(true);

        consumer.handleResolutionRequest(requestEvent("event-1", "idem-1"));

        ArgumentCaptor<EventEnvelope<Map<String, Object>>> envelopeCaptor = envelopeCaptor();
        verify(eventPublisher, times(2)).publish(eq(AppConstants.TOPIC_AUDIENCE_RESOLVED), eq(JOB_ID), envelopeCaptor.capture());

        List<EventEnvelope<Map<String, Object>>> publishedEvents = envelopeCaptor.getAllValues();
        assertThat(publishedEvents).hasSize(2);

        Map<String, Object> firstPayload = publishedEvents.get(0).getPayload();
        assertThat(firstPayload)
                .containsEntry("campaignId", CAMPAIGN_ID)
                .containsEntry("jobId", JOB_ID)
                .containsEntry("chunkId", JOB_ID + ":audience:0")
                .containsEntry("chunkIndex", 0)
                .containsEntry("totalChunks", -1)
                .containsEntry("chunkSize", AppConstants.SEND_BATCH_SIZE)
                .containsEntry("totalResolvedSubscribers", -1)
                .containsEntry("isLastChunk", false);
        assertThat(subscriberPayload(firstPayload)).hasSize(AppConstants.SEND_BATCH_SIZE);
        assertThat(publishedEvents.get(0).getTenantId()).isEqualTo(TENANT_ID);
        assertThat(publishedEvents.get(0).getWorkspaceId()).isEqualTo(WORKSPACE_ID);

        Map<String, Object> secondPayload = publishedEvents.get(1).getPayload();
        assertThat(secondPayload)
                .containsEntry("chunkId", JOB_ID + ":audience:1")
                .containsEntry("chunkIndex", 1)
                .containsEntry("totalChunks", 2)
                .containsEntry("chunkSize", 1)
                .containsEntry("totalResolvedSubscribers", totalSubscribers)
                .containsEntry("isLastChunk", true);
        assertThat(subscriberPayload(secondPayload)).hasSize(1);
    }

    @Test
    void publishesOneLastEmptyChunkWhenNoSubscribersResolve() {
        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-empty"), eq("idem-empty")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(List.of());

        consumer.handleResolutionRequest(requestEvent("event-empty", "idem-empty"));

        ArgumentCaptor<EventEnvelope<Map<String, Object>>> envelopeCaptor = envelopeCaptor();
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_AUDIENCE_RESOLVED), eq(JOB_ID), envelopeCaptor.capture());
        verify(deliverabilityClient, never()).checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList());
        verify(sendEligibilityService, never()).isSendEligible(any());

        Map<String, Object> payload = envelopeCaptor.getValue().getPayload();
        assertThat(payload)
                .containsEntry("chunkId", JOB_ID + ":audience:0")
                .containsEntry("chunkIndex", 0)
                .containsEntry("totalChunks", 1)
                .containsEntry("chunkSize", 0)
                .containsEntry("totalResolvedSubscribers", 0)
                .containsEntry("isLastChunk", true);
        assertThat(subscriberPayload(payload)).isEmpty();
    }

    @Test
    void omittedAudiencesResolveAllWorkspaceSubscribers() {
        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-no-audience"), eq("idem-no-audience")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(List.of());

        consumer.handleResolutionRequest(requestEvent("event-no-audience", "idem-no-audience"));

        AudienceCandidateCriteria criteria = captureCriteria();
        assertThat(criteria.includeAllSubscribers()).isTrue();
        assertThat(criteria.includeListIds()).isEmpty();
        assertThat(criteria.includeSegmentIds()).isEmpty();
        assertThat(criteria.excludeListIds()).isEmpty();
        assertThat(criteria.excludeSegmentIds()).isEmpty();
    }

    @Test
    void emptyAudiencesResolveAllWorkspaceSubscribers() {
        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-empty-audience"), eq("idem-empty-audience")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(List.of());

        consumer.handleResolutionRequest(requestEvent("event-empty-audience", "idem-empty-audience", List.of()));

        AudienceCandidateCriteria criteria = captureCriteria();
        assertThat(criteria.includeAllSubscribers()).isTrue();
        assertThat(criteria.includeListIds()).isEmpty();
        assertThat(criteria.includeSegmentIds()).isEmpty();
        assertThat(criteria.excludeListIds()).isEmpty();
        assertThat(criteria.excludeSegmentIds()).isEmpty();
    }

    @Test
    void checksSuppressionsPerPageAndKeepsChunkTotalsAfterFiltering() {
        List<Subscriber> subscribers = List.of(subscriber(0), subscriber(1), subscriber(2));

        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-suppressed"), eq("idem-suppressed")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(subscribers);
        when(deliverabilityClient.checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList()))
                .thenReturn(Set.of("user1@example.com"));
        when(sendEligibilityService.isSendEligible(any(Subscriber.class))).thenReturn(true);

        consumer.handleResolutionRequest(requestEvent("event-suppressed", "idem-suppressed"));

        ArgumentCaptor<List<String>> emailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(deliverabilityClient).checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), emailsCaptor.capture());
        assertThat(emailsCaptor.getAllValues())
                .allSatisfy(emails -> assertThat(emails).containsExactly("user0@example.com", "user1@example.com", "user2@example.com"));

        ArgumentCaptor<EventEnvelope<Map<String, Object>>> envelopeCaptor = envelopeCaptor();
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_AUDIENCE_RESOLVED), eq(JOB_ID), envelopeCaptor.capture());

        Map<String, Object> payload = envelopeCaptor.getValue().getPayload();
        assertThat(payload)
                .containsEntry("totalResolvedSubscribers", 2)
                .containsEntry("chunkSize", 2)
                .containsEntry("totalChunks", 1)
                .containsEntry("isLastChunk", true);
        assertThat(subscriberPayload(payload))
                .extracting("email")
                .containsExactly("user0@example.com", "user2@example.com");
    }

    @Test
    void rethrowsSuppressionFailuresWithoutPublishingResolvedAudience() {
        List<Subscriber> subscribers = List.of(subscriber(0));

        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-fail"), eq("idem-fail")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(subscribers);
        when(deliverabilityClient.checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList()))
                .thenThrow(new DeliverabilityServiceClient.SuppressionCheckException("suppression unavailable"));

        assertThatThrownBy(() -> consumer.handleResolutionRequest(requestEvent("event-fail", "idem-fail")))
                .isInstanceOf(DeliverabilityServiceClient.SuppressionCheckException.class)
                .hasMessageContaining("suppression unavailable");

        verify(eventPublisher, never()).publish(eq(AppConstants.TOPIC_AUDIENCE_RESOLVED), any(), any());
    }

    @Test
    void rethrowsPublishFailures() {
        List<Subscriber> subscribers = List.of(subscriber(0));

        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-publish-fail"), eq("idem-publish-fail")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(subscribers);
        when(deliverabilityClient.checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList()))
                .thenReturn(Set.of());
        when(sendEligibilityService.isSendEligible(any(Subscriber.class))).thenReturn(true);
        RuntimeException publishFailure = new RuntimeException("publish failed");
        when(eventPublisher.publish(eq(AppConstants.TOPIC_AUDIENCE_RESOLVED), eq(JOB_ID), any()))
                .thenThrow(publishFailure);

        assertThatThrownBy(() -> consumer.handleResolutionRequest(requestEvent("event-publish-fail", "idem-publish-fail")))
                .isSameAs(publishFailure);
    }

    @Test
    void rethrowsAsyncPublishFailures() {
        List<Subscriber> subscribers = List.of(subscriber(0));

        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-async-publish-fail"), eq("idem-async-publish-fail")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(subscribers);
        when(deliverabilityClient.checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList()))
                .thenReturn(Set.of());
        when(sendEligibilityService.isSendEligible(any(Subscriber.class))).thenReturn(true);
        RuntimeException publishFailure = new RuntimeException("async publish failed");
        when(eventPublisher.publish(eq(AppConstants.TOPIC_AUDIENCE_RESOLVED), eq(JOB_ID), any()))
                .thenReturn(CompletableFuture.failedFuture(publishFailure));

        assertThatThrownBy(() -> consumer.handleResolutionRequest(requestEvent("event-async-publish-fail", "idem-async-publish-fail")))
                .isSameAs(publishFailure);
    }

    @Test
    void listenerIsTransactionalSoFailedSideEffectsDoNotCommitIdempotency() throws NoSuchMethodException {
        Transactional transactional = AudienceResolutionConsumer.class
                .getMethod("handleResolutionRequest", EventEnvelope.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void resolvesListIncludeWithDbBackedCandidateCriteria() {
        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-list"), eq("idem-list")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(List.of(subscriber(0)));
        when(deliverabilityClient.checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList()))
                .thenReturn(Set.of());
        when(sendEligibilityService.isSendEligible(any(Subscriber.class))).thenReturn(true);

        consumer.handleResolutionRequest(requestEvent(
                "event-list",
                "idem-list",
                List.of(Map.of("type", "LIST", "id", "list-1", "action", "INCLUDE"))));

        AudienceCandidateCriteria criteria = captureCriteria();
        assertThat(criteria.includeAllSubscribers()).isFalse();
        assertThat(criteria.includeListIds()).containsExactly("list-1");
        assertThat(criteria.includeSegmentIds()).isEmpty();
        assertThat(criteria.excludeListIds()).isEmpty();
        assertThat(criteria.excludeSegmentIds()).isEmpty();
    }

    @Test
    void resolvesSegmentIncludeWithDbBackedCandidateCriteria() {
        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-segment"), eq("idem-segment")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(List.of(subscriber(0)));
        when(deliverabilityClient.checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList()))
                .thenReturn(Set.of());
        when(sendEligibilityService.isSendEligible(any(Subscriber.class))).thenReturn(true);

        consumer.handleResolutionRequest(requestEvent(
                "event-segment",
                "idem-segment",
                List.of(Map.of("type", "SEGMENT", "id", "segment-1", "action", "INCLUDE"))));

        AudienceCandidateCriteria criteria = captureCriteria();
        assertThat(criteria.includeAllSubscribers()).isFalse();
        assertThat(criteria.includeListIds()).isEmpty();
        assertThat(criteria.includeSegmentIds()).containsExactly("segment-1");
        assertThat(criteria.excludeListIds()).isEmpty();
        assertThat(criteria.excludeSegmentIds()).isEmpty();
    }

    @Test
    void resolvesIncludeAndExcludeWithDbBackedCandidateCriteria() {
        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-mixed"), eq("idem-mixed")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(List.of(subscriber(0)));
        when(deliverabilityClient.checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList()))
                .thenReturn(Set.of());
        when(sendEligibilityService.isSendEligible(any(Subscriber.class))).thenReturn(true);

        consumer.handleResolutionRequest(requestEvent(
                "event-mixed",
                "idem-mixed",
                List.of(
                        Map.of("type", "LIST", "id", "list-1", "action", "INCLUDE"),
                        Map.of("type", "SEGMENT", "id", "segment-2", "action", "EXCLUDE"))));

        AudienceCandidateCriteria criteria = captureCriteria();
        assertThat(criteria.includeAllSubscribers()).isFalse();
        assertThat(criteria.includeListIds()).containsExactly("list-1");
        assertThat(criteria.includeSegmentIds()).isEmpty();
        assertThat(criteria.excludeListIds()).isEmpty();
        assertThat(criteria.excludeSegmentIds()).containsExactly("segment-2");
    }

    @Test
    void resolvesExcludeOnlyAgainstAllSubscribersWithDbBackedCandidateCriteria() {
        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-exclude"), eq("idem-exclude")))
                .thenReturn(true);
        when(audienceCandidateRepository.findNextCandidates(eq(TENANT_ID), eq(WORKSPACE_ID), any(AudienceCandidateCriteria.class), eq(null), eq(resolutionPage())))
                .thenReturn(List.of(subscriber(0)));
        when(deliverabilityClient.checkSuppressedEmails(eq(TENANT_ID), eq(WORKSPACE_ID), anyList()))
                .thenReturn(Set.of());
        when(sendEligibilityService.isSendEligible(any(Subscriber.class))).thenReturn(true);

        consumer.handleResolutionRequest(requestEvent(
                "event-exclude",
                "idem-exclude",
                List.of(Map.of("type", "LIST", "id", "list-2", "action", "EXCLUDE"))));

        AudienceCandidateCriteria criteria = captureCriteria();
        assertThat(criteria.includeAllSubscribers()).isTrue();
        assertThat(criteria.includeListIds()).isEmpty();
        assertThat(criteria.includeSegmentIds()).isEmpty();
        assertThat(criteria.excludeListIds()).containsExactly("list-2");
        assertThat(criteria.excludeSegmentIds()).isEmpty();
    }

    @Test
    void duplicateResolutionRequestReturnsBeforeQueryingCandidates() {
        when(idempotencyService.registerIfNew(eq(TENANT_ID), eq(WORKSPACE_ID), eq(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED), eq("event-duplicate"), eq("idem-duplicate")))
                .thenReturn(false);

        consumer.handleResolutionRequest(requestEvent("event-duplicate", "idem-duplicate"));

        verify(audienceCandidateRepository, never()).findNextCandidates(any(), any(), any(), any(), any());
        verify(deliverabilityClient, never()).checkSuppressedEmails(any(), any(), anyList());
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    private EventEnvelope<Map<String, Object>> requestEvent(String eventId, String idempotencyKey) {
        return EventEnvelope.<Map<String, Object>>builder()
                .eventId(eventId)
                .eventType(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED)
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .idempotencyKey(idempotencyKey)
                .payload(Map.of(
                        "campaignId", CAMPAIGN_ID,
                        "jobId", JOB_ID
                ))
                .build();
    }

    private EventEnvelope<Map<String, Object>> requestEvent(
            String eventId,
            String idempotencyKey,
            List<Map<String, String>> audiences) {
        return EventEnvelope.<Map<String, Object>>builder()
                .eventId(eventId)
                .eventType(AppConstants.TOPIC_AUDIENCE_RESOLUTION_REQUESTED)
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .idempotencyKey(idempotencyKey)
                .payload(Map.of(
                        "campaignId", CAMPAIGN_ID,
                        "jobId", JOB_ID,
                        "audiences", audiences
                ))
                .build();
    }

    private Subscriber subscriber(int index) {
        Subscriber subscriber = new Subscriber();
        subscriber.setId("subscriber-" + index);
        subscriber.setTenantId(TENANT_ID);
        subscriber.setWorkspaceId(WORKSPACE_ID);
        subscriber.setSubscriberKey("key-" + index);
        subscriber.setEmail("user" + index + "@example.com");
        return subscriber;
    }

    private PageRequest resolutionPage() {
        return PageRequest.of(0, AppConstants.SEND_BATCH_SIZE);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<EventEnvelope<Map<String, Object>>> envelopeCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(EventEnvelope.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AudienceCandidateCriteria captureCriteria() {
        ArgumentCaptor<AudienceCandidateCriteria> criteriaCaptor = ArgumentCaptor.forClass(AudienceCandidateCriteria.class);
        verify(audienceCandidateRepository).findNextCandidates(
                eq(TENANT_ID),
                eq(WORKSPACE_ID),
                criteriaCaptor.capture(),
                eq(null),
                eq(resolutionPage()));
        return criteriaCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> subscriberPayload(Map<String, Object> payload) {
        return (List<Map<String, String>>) payload.get("subscribers");
    }
}
