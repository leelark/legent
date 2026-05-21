package com.legent.tracking.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.tracking.domain.RawEvent;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.service.ClickHouseWriter;
import com.legent.tracking.service.TrackingEventFinalizationService;
import com.legent.tracking.service.TrackingEventIdempotencyService;
import com.legent.tracking.service.TrackingEventIdempotencyService.ClaimStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingEventConsumerTest {

    @Mock private ClickHouseWriter clickHouseWriter;
    @Mock private TrackingEventFinalizationService finalizationService;
    @Mock private TrackingEventIdempotencyService idempotencyService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private TrackingEventConsumer consumer;

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_TwoValidEnvelopes_WritesOneRawClickHouseBatch() throws Exception {
        EventEnvelope<String> openEnvelope = envelope("evt-open", "idem-open", "{}");
        EventEnvelope<String> clickEnvelope = envelope("evt-click", "idem-click", "{}");
        TrackingDto.RawEventPayload openPayload = payload("OPEN");
        TrackingDto.RawEventPayload clickPayload = payload("CLICK");

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(openPayload, clickPayload);
        when(idempotencyService.claimForProcessing(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ClaimStatus.CLAIMED);
        ArgumentCaptor<List<TrackingDto.RawEventPayload>> rawBatchCaptor = ArgumentCaptor.captor();

        consumer.handleIngestedEvents(List.of(openEnvelope, clickEnvelope));

        verify(clickHouseWriter).writeBatch(rawBatchCaptor.capture());
        assertEquals(List.of(openPayload, clickPayload), rawBatchCaptor.getValue());
        assertEquals("evt-open", openPayload.getId());
        assertEquals("evt-click", clickPayload.getId());
        verify(idempotencyService).markRawWritten("tenant-1", "workspace-1", "OPEN", "evt-open", "idem-open");
        verify(idempotencyService).markRawWritten("tenant-1", "workspace-1", "CLICK", "evt-click", "idem-click");
        verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("OPEN"), eq("evt-open"), eq("idem-open"));
        verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("CLICK"), eq("evt-click"), eq("idem-click"));
        verify(idempotencyService, never()).releaseClaim(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_InProgressStatus_RetriesWithoutSideEffects() throws Exception {
        EventEnvelope<String> inProgressEnvelope = envelope("evt-in-progress", "idem-in-progress", "{}");
        EventEnvelope<String> processedEnvelope = envelope("evt-processed", "idem-processed", "{}");
        TrackingDto.RawEventPayload inProgressPayload = payload("CLICK");
        TrackingDto.RawEventPayload processedPayload = payload("CONVERSION");

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(inProgressPayload, processedPayload);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "CLICK", "evt-in-progress", "idem-in-progress"))
                .thenReturn(ClaimStatus.IN_PROGRESS);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "CONVERSION", "evt-processed", "idem-processed"))
                .thenReturn(ClaimStatus.PROCESSED);

        assertThrows(IllegalStateException.class,
                () -> consumer.handleIngestedEvents(List.of(inProgressEnvelope, processedEnvelope)));

        verifyNoInteractions(clickHouseWriter, finalizationService);
        verify(idempotencyService, never()).markRawWritten(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(idempotencyService, never()).releaseClaim(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_RawWrittenStatus_SkipsClickHouseAndCallsFinalizer() throws Exception {
        EventEnvelope<String> envelope = envelope("evt-raw-written", "idem-raw-written", "{}");
        TrackingDto.RawEventPayload payload = payload("OPEN");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "OPEN", "evt-raw-written", "idem-raw-written"))
                .thenReturn(ClaimStatus.RAW_WRITTEN);

        consumer.handleIngestedEvents(List.of(envelope));

        verifyNoInteractions(clickHouseWriter);
        verify(idempotencyService, never()).markRawWritten(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(idempotencyService, never()).releaseClaim(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("OPEN"), eq("evt-raw-written"), eq("idem-raw-written"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_MixedClaimedAndRawWritten_FinalizesInEncounterOrder() throws Exception {
        EventEnvelope<String> openEnvelope = envelope("evt-open", "idem-open", "{}");
        EventEnvelope<String> clickEnvelope = envelope("evt-click", "idem-click", "{}");
        EventEnvelope<String> conversionEnvelope = envelope("evt-conversion", "idem-conversion", "{}");
        TrackingDto.RawEventPayload openPayload = payload("OPEN");
        TrackingDto.RawEventPayload clickPayload = payload("CLICK");
        TrackingDto.RawEventPayload conversionPayload = payload("CONVERSION");

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(openPayload, clickPayload, conversionPayload);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "OPEN", "evt-open", "idem-open"))
                .thenReturn(ClaimStatus.CLAIMED);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "CLICK", "evt-click", "idem-click"))
                .thenReturn(ClaimStatus.RAW_WRITTEN);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "CONVERSION", "evt-conversion", "idem-conversion"))
                .thenReturn(ClaimStatus.CLAIMED);
        ArgumentCaptor<List<TrackingDto.RawEventPayload>> rawBatchCaptor = ArgumentCaptor.captor();

        consumer.handleIngestedEvents(List.of(openEnvelope, clickEnvelope, conversionEnvelope));

        verify(clickHouseWriter).writeBatch(rawBatchCaptor.capture());
        assertEquals(List.of(openPayload, conversionPayload), rawBatchCaptor.getValue());
        InOrder finalizationOrder = inOrder(finalizationService);
        finalizationOrder.verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("OPEN"), eq("evt-open"), eq("idem-open"));
        finalizationOrder.verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("CLICK"), eq("evt-click"), eq("idem-click"));
        finalizationOrder.verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("CONVERSION"), eq("evt-conversion"), eq("idem-conversion"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_DuplicateRawWrittenInSameBatch_FinalizesOnce() throws Exception {
        EventEnvelope<String> firstEnvelope = envelope("evt-raw-written", "idem-raw-written", "{}");
        EventEnvelope<String> duplicateEnvelope = envelope("evt-raw-written", "idem-raw-written", "{}");
        TrackingDto.RawEventPayload firstPayload = payload("OPEN");
        TrackingDto.RawEventPayload duplicatePayload = payload("OPEN");

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(firstPayload, duplicatePayload);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "OPEN", "evt-raw-written", "idem-raw-written"))
                .thenReturn(ClaimStatus.RAW_WRITTEN);

        consumer.handleIngestedEvents(List.of(firstEnvelope, duplicateEnvelope));

        verifyNoInteractions(clickHouseWriter);
        verify(idempotencyService).claimForProcessing(
                "tenant-1", "workspace-1", "OPEN", "evt-raw-written", "idem-raw-written");
        verify(idempotencyService, never()).markRawWritten(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("OPEN"), eq("evt-raw-written"), eq("idem-raw-written"));
        verifyNoMoreInteractions(finalizationService);
    }

    @Test
    void handleIngestedEvents_MissingWorkspace_ThrowsWithoutSideEffects() {
        EventEnvelope<String> envelope = envelope("evt-1", "idem-1", "{}");
        envelope.setWorkspaceId(null);

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(envelope)));

        verifyNoInteractions(idempotencyService, finalizationService, clickHouseWriter);
    }

    @Test
    void handleIngestedEvents_MissingTenant_ThrowsWithoutSideEffects() {
        EventEnvelope<String> envelope = envelope("evt-1", "idem-1", "{}");
        envelope.setTenantId(null);

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(envelope)));

        verifyNoInteractions(idempotencyService, finalizationService, clickHouseWriter);
    }

    @Test
    void handleIngestedEvents_MissingEventId_ThrowsWithoutSideEffects() {
        EventEnvelope<String> envelope = envelope("evt-1", "idem-1", "{}");
        envelope.setEventId(null);

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(envelope)));

        verifyNoInteractions(idempotencyService, finalizationService, clickHouseWriter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_MalformedPayload_ThrowsWithoutSideEffects() throws Exception {
        EventEnvelope<String> envelope = envelope("evt-malformed", "idem-malformed", "{not-json");

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("bad payload") {});

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(envelope)));

        verifyNoInteractions(idempotencyService, finalizationService, clickHouseWriter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_TenantMismatch_ThrowsWithoutSideEffects() throws Exception {
        EventEnvelope<String> envelope = envelope("evt-tenant-mismatch", "idem-tenant-mismatch", "{}");
        TrackingDto.RawEventPayload payload = payload("OPEN");
        payload.setTenantId("tenant-payload");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(envelope)));

        verifyNoInteractions(idempotencyService, finalizationService, clickHouseWriter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_WorkspaceMismatch_ThrowsWithoutSideEffects() throws Exception {
        EventEnvelope<String> envelope = envelope("evt-workspace-mismatch", "idem-workspace-mismatch", "{}");
        TrackingDto.RawEventPayload payload = payload("OPEN");
        payload.setWorkspaceId("workspace-payload");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(envelope)));

        verifyNoInteractions(idempotencyService, finalizationService, clickHouseWriter);
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_MarkRawWrittenFailureAfterRawWrite_RetriesAndFinalizesOnlyKnownRawWritten() throws Exception {
        EventEnvelope<String> newEnvelope = envelope("evt-new", "idem-new", "{}");
        EventEnvelope<String> rawWrittenEnvelope = envelope("evt-raw-written", "idem-raw-written", "{}");
        TrackingDto.RawEventPayload newPayload = payload("OPEN");
        TrackingDto.RawEventPayload rawWrittenPayload = payload("CLICK");

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(newPayload, rawWrittenPayload);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "OPEN", "evt-new", "idem-new"))
                .thenReturn(ClaimStatus.CLAIMED);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "CLICK", "evt-raw-written", "idem-raw-written"))
                .thenReturn(ClaimStatus.RAW_WRITTEN);
        doThrow(new RuntimeException("ledger down"))
                .when(idempotencyService).markRawWritten("tenant-1", "workspace-1", "OPEN", "evt-new", "idem-new");
        ArgumentCaptor<List<TrackingDto.RawEventPayload>> rawBatchCaptor = ArgumentCaptor.captor();

        assertThrows(IllegalStateException.class,
                () -> consumer.handleIngestedEvents(List.of(newEnvelope, rawWrittenEnvelope)));

        verify(clickHouseWriter).writeBatch(rawBatchCaptor.capture());
        assertEquals(List.of(newPayload), rawBatchCaptor.getValue());
        verify(idempotencyService, never()).releaseClaim(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(finalizationService, never()).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("OPEN"), eq("evt-new"), eq("idem-new"));
        verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("CLICK"), eq("evt-raw-written"), eq("idem-raw-written"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_ClickHouseFailure_ReleasesOnlyNewClaimsAndRetrySucceeds() throws Exception {
        EventEnvelope<String> newEnvelope = envelope("evt-new", "idem-new", "{}");
        EventEnvelope<String> rawWrittenEnvelope = envelope("evt-raw-written", "idem-raw-written", "{}");
        TrackingDto.RawEventPayload newPayload = payload("OPEN");
        TrackingDto.RawEventPayload rawWrittenPayload = payload("CLICK");

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(newPayload, rawWrittenPayload, newPayload, rawWrittenPayload);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "OPEN", "evt-new", "idem-new"))
                .thenReturn(ClaimStatus.CLAIMED, ClaimStatus.CLAIMED);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "CLICK", "evt-raw-written", "idem-raw-written"))
                .thenReturn(ClaimStatus.RAW_WRITTEN, ClaimStatus.RAW_WRITTEN);
        doThrow(new RuntimeException("clickhouse down"))
                .doNothing()
                .when(clickHouseWriter).writeBatch(anyList());
        ArgumentCaptor<List<TrackingDto.RawEventPayload>> rawBatchCaptor = ArgumentCaptor.captor();

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(newEnvelope, rawWrittenEnvelope)));
        consumer.handleIngestedEvents(List.of(newEnvelope, rawWrittenEnvelope));

        verify(idempotencyService).releaseClaim("tenant-1", "workspace-1", "OPEN", "evt-new", "idem-new");
        verify(idempotencyService, never()).releaseClaim("tenant-1", "workspace-1", "CLICK", "evt-raw-written", "idem-raw-written");
        verify(idempotencyService).markRawWritten("tenant-1", "workspace-1", "OPEN", "evt-new", "idem-new");
        verify(idempotencyService, never()).markRawWritten("tenant-1", "workspace-1", "CLICK", "evt-raw-written", "idem-raw-written");
        verify(clickHouseWriter, times(2)).writeBatch(rawBatchCaptor.capture());
        assertEquals(List.of(newPayload), rawBatchCaptor.getAllValues().get(0));
        assertEquals(List.of(newPayload), rawBatchCaptor.getAllValues().get(1));
        verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("OPEN"), eq("evt-new"), eq("idem-new"));
        verify(finalizationService).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("CLICK"), eq("evt-raw-written"), eq("idem-raw-written"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_FinalizationFailureAfterRawWrite_DoesNotReleaseClaim() throws Exception {
        EventEnvelope<String> envelope = envelope("evt-1", "idem-1", "{}");
        TrackingDto.RawEventPayload payload = payload("OPEN");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "OPEN", "evt-1", "idem-1"))
                .thenReturn(ClaimStatus.CLAIMED);
        doThrow(new RuntimeException("finalization down"))
                .when(finalizationService).finalizeEvent(
                        any(RawEvent.class), anyString(), anyString(), anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(envelope)));

        verify(clickHouseWriter).writeBatch(anyList());
        verify(idempotencyService).markRawWritten("tenant-1", "workspace-1", "OPEN", "evt-1", "idem-1");
        verify(idempotencyService, never()).releaseClaim(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void handleIngestedEvents_SecondDeliveryAfterFinalizationFailure_DoesNotRewriteClickHouse() throws Exception {
        EventEnvelope<String> envelope = envelope("evt-1", "idem-1", "{}");
        TrackingDto.RawEventPayload payload = payload("OPEN");

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(payload, payload);
        when(idempotencyService.claimForProcessing("tenant-1", "workspace-1", "OPEN", "evt-1", "idem-1"))
                .thenReturn(ClaimStatus.CLAIMED, ClaimStatus.RAW_WRITTEN);
        doThrow(new RuntimeException("finalization down"))
                .doNothing()
                .when(finalizationService).finalizeEvent(
                        any(RawEvent.class), anyString(), anyString(), anyString(), anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> consumer.handleIngestedEvents(List.of(envelope)));
        consumer.handleIngestedEvents(List.of(envelope));

        verify(clickHouseWriter).writeBatch(anyList());
        verify(idempotencyService).markRawWritten("tenant-1", "workspace-1", "OPEN", "evt-1", "idem-1");
        verify(finalizationService, times(2)).finalizeEvent(
                any(RawEvent.class), eq("tenant-1"), eq("workspace-1"), eq("OPEN"), eq("evt-1"), eq("idem-1"));
        verify(idempotencyService, never()).releaseClaim(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handleIngestedEvents_ListenerAnnotationUsesTrackingBatchFactory() throws Exception {
        Method batchListener = TrackingEventConsumer.class.getDeclaredMethod("handleIngestedEvents", List.class);

        KafkaListener annotation = batchListener.getAnnotation(KafkaListener.class);

        assertNotNull(annotation);
        assertTrue(Arrays.asList(annotation.topics()).contains(AppConstants.TOPIC_TRACKING_INGESTED));
        assertEquals("tracking-clickhouse-group", annotation.groupId());
        assertEquals("trackingIngestedKafkaListenerContainerFactory", annotation.containerFactory());
    }

    private EventEnvelope<String> envelope(String eventId, String idempotencyKey, String payload) {
        EventEnvelope<String> envelope = new EventEnvelope<>();
        envelope.setTenantId("tenant-1");
        envelope.setWorkspaceId("workspace-1");
        envelope.setEventType(AppConstants.TOPIC_TRACKING_INGESTED);
        envelope.setEventId(eventId);
        envelope.setIdempotencyKey(idempotencyKey);
        envelope.setPayload(payload);
        return envelope;
    }

    private TrackingDto.RawEventPayload payload(String eventType) {
        return TrackingDto.RawEventPayload.builder()
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .eventType(eventType)
                .timestamp(Instant.now())
                .build();
    }
}
