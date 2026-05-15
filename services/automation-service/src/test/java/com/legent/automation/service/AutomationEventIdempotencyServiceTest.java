package com.legent.automation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationEventIdempotencyServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private AutomationEventIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new AutomationEventIdempotencyService(jdbcTemplate);
    }

    @Test
    void claimIfNewInsertsPendingClaim() {
        when(jdbcTemplate.update(
                anyString(),
                any(),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow.trigger"),
                eq("evt-1"),
                eq("idem-1"))).thenReturn(1);

        assertThat(service.claimIfNew("tenant-1", "workspace-1", "workflow.trigger", "evt-1", "idem-1"))
                .isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                any(),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow.trigger"),
                eq("evt-1"),
                eq("idem-1"));
        assertThat(sqlCaptor.getValue()).contains("processed_at").contains("NULL");
    }

    @Test
    void markProcessedOnlyCompletesPendingClaim() {
        when(jdbcTemplate.update(
                anyString(),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow.trigger"),
                eq("evt-1"))).thenReturn(1);

        service.markProcessed("tenant-1", "workspace-1", "workflow.trigger", "evt-1", "idem-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow.trigger"),
                eq("evt-1"));
        assertThat(sqlCaptor.getValue()).contains("processed_at IS NULL");
    }

    @Test
    void releaseClaimDeletesOnlyPendingClaim() {
        service.releaseClaim("tenant-1", "workspace-1", "workflow.trigger", "evt-1", "idem-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("workflow.trigger"),
                eq("evt-1"));
        assertThat(sqlCaptor.getValue()).contains("DELETE FROM automation_event_idempotency");
        assertThat(sqlCaptor.getValue()).contains("processed_at IS NULL");
    }

    @Test
    void claimRequiresEventIdOrIdempotencyKey() {
        assertThatThrownBy(() -> service.claimIfNew("tenant-1", "workspace-1", "workflow.trigger", " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId or idempotencyKey");
    }
}
