package com.legent.tracking.service;

import com.legent.tracking.domain.RawEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TrackingEventFinalizationService {

    private final AggregationService aggregationService;
    private final TrackingEventIdempotencyService idempotencyService;

    @Transactional
    public void finalizeEvent(RawEvent rawEvent,
                              String tenantId,
                              String workspaceId,
                              String eventType,
                              String eventId,
                              String idempotencyKey) {
        finalizeEvent(new FinalizationCommand(rawEvent, tenantId, workspaceId, eventType, eventId, idempotencyKey));
    }

    @Transactional
    public void finalizeEvent(FinalizationCommand command) {
        Objects.requireNonNull(command, "command is required");
        aggregationService.aggregateEvent(command.rawEvent());
        idempotencyService.markProcessed(
                command.tenantId(),
                command.workspaceId(),
                command.eventType(),
                command.eventId(),
                command.idempotencyKey());
    }

    public record FinalizationCommand(
            RawEvent rawEvent,
            String tenantId,
            String workspaceId,
            String eventType,
            String eventId,
            String idempotencyKey) {

        public FinalizationCommand {
            rawEvent = Objects.requireNonNull(rawEvent, "rawEvent is required");
            tenantId = requireNonBlank(tenantId, "tenantId");
            workspaceId = requireNonBlank(workspaceId, "workspaceId");
            eventType = requireNonBlank(eventType, "eventType");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
