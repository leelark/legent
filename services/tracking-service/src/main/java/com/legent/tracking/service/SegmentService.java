package com.legent.tracking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SegmentService {
    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getSegment(String field, String value) {
        // Example: segment by audience, device, etc.
        return jdbcTemplate.queryForList("""
            SELECT event_type, count(*) AS count
            FROM tracking_events
            WHERE payload->>? = ?
            GROUP BY event_type
        """, field, value);
    }
}
