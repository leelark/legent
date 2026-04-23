package com.legent.tracking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getEventCounts(String tenantId) {
        return jdbcTemplate.queryForList("""
            SELECT event_type, count(*) AS count
            FROM raw_events
            WHERE tenant_id = ?
            GROUP BY event_type
        """, tenantId);
    }

    public List<Map<String, Object>> getEventTimeline(String tenantId, String eventType) {
        return jdbcTemplate.queryForList("""
            SELECT date_trunc('hour', timestamp) AS hour, count(*) AS count
            FROM raw_events
            WHERE tenant_id = ? AND event_type = ?
            GROUP BY hour
            ORDER BY hour
        """, tenantId, eventType);
    }
}
