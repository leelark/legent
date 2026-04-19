package com.legent.tracking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FunnelService {
    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> getFunnel(String campaignId) {
        // Example: open, click, conversion counts for a campaign
        return jdbcTemplate.queryForList("""
            SELECT event_type, count(*) AS count
            FROM tracking_events
            WHERE payload->>'campaignId' = ?
            GROUP BY event_type
        """, campaignId);
    }
}
