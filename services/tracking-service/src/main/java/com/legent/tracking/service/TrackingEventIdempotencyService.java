package com.legent.tracking.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class TrackingEventIdempotencyService {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public boolean registerIfNew(String tenantId,
                                 String workspaceId,
                                 String eventType,
                                 String eventId,
                                 String idempotencyKey) {
        try {
            entityManager.createNativeQuery("""
                    INSERT INTO tracking_event_idempotency
                    (id, tenant_id, workspace_id, event_type, event_id, idempotency_key, processed_at, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """)
                    .setParameter(1, UUID.randomUUID().toString())
                    .setParameter(2, tenantId)
                    .setParameter(3, workspaceId)
                    .setParameter(4, eventType)
                    .setParameter(5, eventId)
                    .setParameter(6, normalize(idempotencyKey))
                    .setParameter(7, Instant.now())
                    .setParameter(8, Instant.now())
                    .setParameter(9, Instant.now())
                    .executeUpdate();
            return true;
        } catch (Exception e) {
            if (isDuplicateKey(e)) {
                log.debug("Skipping duplicate tracking event tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                        tenantId, workspaceId, eventType, eventId, idempotencyKey);
                return false;
            }
            throw e;
        }
    }

    private boolean isDuplicateKey(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("duplicate key") || normalized.contains("23505");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
