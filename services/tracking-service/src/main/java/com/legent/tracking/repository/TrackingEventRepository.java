package com.legent.tracking.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TrackingEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public void saveOpen(String mid, String tenantId) {
        jdbcTemplate.update("INSERT INTO raw_events (id, tenant_id, message_id, event_type) VALUES (?, ?, ?, ?)", 
            UUID.randomUUID().toString(), tenantId, mid, "OPEN");
    }

    public void saveClick(String mid, String tenantId, String url) {
        jdbcTemplate.update("INSERT INTO raw_events (id, tenant_id, message_id, event_type, link_url) VALUES (?, ?, ?, ?, ?)", 
            UUID.randomUUID().toString(), tenantId, mid, "CLICK", url);
    }

    public void saveConversion(String mid, String tenantId, String payload) {
        jdbcTemplate.update("INSERT INTO raw_events (id, tenant_id, message_id, event_type, metadata) VALUES (?, ?, ?, ?, ?::jsonb)", 
            UUID.randomUUID().toString(), tenantId, mid, "CONVERSION", payload);
    }
}
