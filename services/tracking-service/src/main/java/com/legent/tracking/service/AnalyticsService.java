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

    public List<Map<String, Object>> getEventCounts() {
        return jdbcTemplate.queryForList("""
            SELECT event_type, count(*) AS count
            FROM tracking_events
            GROUP BY event_type
        """);
    }

    public List<Map<String, Object>> getEventTimeline(String eventType) {
        return jdbcTemplate.queryForList("""
            SELECT date_trunc('hour', created_at) AS hour, count(*) AS count
            FROM tracking_events
            WHERE event_type = ?
            GROUP BY hour
            ORDER BY hour
        """, eventType);
    }
}
