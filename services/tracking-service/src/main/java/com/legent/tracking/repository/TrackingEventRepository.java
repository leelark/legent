package com.legent.tracking.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TrackingEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public void saveOpen(String mid) {
        jdbcTemplate.update("INSERT INTO tracking_events (mid, event_type) VALUES (?, ?)", mid, "open");
    }

    public void saveClick(String mid, String url) {
        jdbcTemplate.update("INSERT INTO tracking_events (mid, event_type, url) VALUES (?, ?, ?)", mid, "click", url);
    }

    public void saveConversion(String mid, String payload) {
        jdbcTemplate.update("INSERT INTO tracking_events (mid, event_type, payload) VALUES (?, ?, ?::jsonb)", mid, "conversion", payload);
    }
}
