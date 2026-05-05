package com.legent.audience.service;

import com.legent.common.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exactly-once guard for audience-side event handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AudienceEventIdempotencyService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public boolean registerIfNew(String tenantId,
                                 String workspaceId,
                                 String eventType,
                                 String eventId,
                                 String idempotencyKey) {
        String sql = """
                INSERT INTO audience_event_idempotency
                (id, tenant_id, workspace_id, event_type, event_id, idempotency_key, processed_at, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW(), NOW(), 0)
                ON CONFLICT DO NOTHING
                """;
        int rows = jdbcTemplate.update(
                sql,
                IdGenerator.newId(),
                tenantId,
                workspaceId,
                eventType,
                normalize(eventId),
                normalize(idempotencyKey)
        );
        if (rows == 0) {
            log.debug("Skipping duplicate audience event tenant={}, workspace={}, type={}, eventId={}, idemKey={}",
                    tenantId, workspaceId, eventType, eventId, idempotencyKey);
            return false;
        }
        return true;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
