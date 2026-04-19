package com.legent.tracking.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsServiceTest {
    @Test
    void getEventCounts_executesQuery() {
        var jdbc = Mockito.mock(JdbcTemplate.class);
        var service = new AnalyticsService(jdbc);
        service.getEventCounts();
        Mockito.verify(jdbc).queryForList(Mockito.anyString());
    }

    @Test
    void getEventTimeline_executesQuery() {
        var jdbc = Mockito.mock(JdbcTemplate.class);
        var service = new AnalyticsService(jdbc);
        service.getEventTimeline("open");
        Mockito.verify(jdbc).queryForList(Mockito.anyString(), Mockito.eq("open"));
    }
}
