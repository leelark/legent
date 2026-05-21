package com.legent.tracking.service;

import com.legent.tracking.domain.RawEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TrackingEventFinalizationServiceTest {

    @Mock private AggregationService aggregationService;
    @Mock private TrackingEventIdempotencyService idempotencyService;

    @InjectMocks private TrackingEventFinalizationService service;

    @Test
    void finalizeEvent_AggregatesBeforeMarkProcessed() {
        RawEvent rawEvent = rawEvent();

        service.finalizeEvent(rawEvent, "tenant-1", "workspace-1", "OPEN", "evt-1", "idem-1");

        InOrder inOrder = inOrder(aggregationService, idempotencyService);
        inOrder.verify(aggregationService).aggregateEvent(rawEvent);
        inOrder.verify(idempotencyService).markProcessed(
                "tenant-1", "workspace-1", "OPEN", "evt-1", "idem-1");
    }

    @Test
    void finalizeEvent_WhenAggregationThrows_DoesNotMarkProcessed() {
        RawEvent rawEvent = rawEvent();
        RuntimeException failure = new RuntimeException("aggregation down");
        TrackingEventFinalizationService.FinalizationCommand command =
                new TrackingEventFinalizationService.FinalizationCommand(
                        rawEvent, "tenant-1", "workspace-1", "OPEN", "evt-1", "idem-1");
        doThrow(failure).when(aggregationService).aggregateEvent(rawEvent);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.finalizeEvent(command));

        assertSame(failure, thrown);
        verifyNoInteractions(idempotencyService);
    }

    @Test
    void finalizeEvent_MethodsAreTransactional() throws Exception {
        Method rawEventMethod = TrackingEventFinalizationService.class.getDeclaredMethod(
                "finalizeEvent",
                RawEvent.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class);
        Method commandMethod = TrackingEventFinalizationService.class.getDeclaredMethod(
                "finalizeEvent",
                TrackingEventFinalizationService.FinalizationCommand.class);

        assertNotNull(rawEventMethod.getAnnotation(Transactional.class));
        assertNotNull(commandMethod.getAnnotation(Transactional.class));
    }

    private RawEvent rawEvent() {
        RawEvent rawEvent = new RawEvent();
        rawEvent.setId("evt-1");
        rawEvent.setTenantId("tenant-1");
        rawEvent.setWorkspaceId("workspace-1");
        rawEvent.setEventType("OPEN");
        rawEvent.setTimestamp(Instant.parse("2026-05-20T00:00:00Z"));
        return rawEvent;
    }
}
