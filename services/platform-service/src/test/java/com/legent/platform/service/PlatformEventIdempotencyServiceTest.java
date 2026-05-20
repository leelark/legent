package com.legent.platform.service;

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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformEventIdempotencyServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private PlatformEventIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new PlatformEventIdempotencyService(jdbcTemplate);
    }

    @Test
    void claimIfNewInsertsPendingClaim() {
        when(jdbcTemplate.update(
                anyString(),
                any(),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("notification.created"),
                eq("evt-1"),
                eq("idem-1"))).thenReturn(1);

        assertThat(service.claimIfNew("tenant-1", "workspace-1", "notification.created", "evt-1", "idem-1"))
                .isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                any(),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("notification.created"),
                eq("evt-1"),
                eq("idem-1"));
        assertThat(sqlCaptor.getValue())
                .contains("platform_event_idempotency")
                .contains("processed_at")
                .contains("NULL")
                .contains("ON CONFLICT DO NOTHING");
    }

    @Test
    void duplicateClaimReturnsFalse() {
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThat(service.claimIfNew("tenant-1", null, "webhook.triggered", "evt-1", "idem-1"))
                .isFalse();
    }

    @Test
    void markProcessedOnlyCompletesPendingClaimAndSupportsNullWorkspace() {
        when(jdbcTemplate.update(
                anyString(),
                eq("tenant-1"),
                isNull(),
                eq("webhook.triggered"),
                eq("evt-1"))).thenReturn(1);

        service.markProcessed("tenant-1", null, "webhook.triggered", "evt-1", "idem-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq("tenant-1"),
                isNull(),
                eq("webhook.triggered"),
                eq("evt-1"));
        assertThat(sqlCaptor.getValue())
                .contains("workspace_id IS NOT DISTINCT FROM ?")
                .contains("processed_at IS NULL")
                .contains("event_id");
    }

    @Test
    void releaseClaimDeletesOnlyPendingClaim() {
        service.releaseClaim("tenant-1", "workspace-1", "search.index.updated", null, "idem-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(
                sqlCaptor.capture(),
                eq("tenant-1"),
                eq("workspace-1"),
                eq("search.index.updated"),
                eq("idem-1"));
        assertThat(sqlCaptor.getValue())
                .contains("DELETE FROM platform_event_idempotency")
                .contains("workspace_id IS NOT DISTINCT FROM ?")
                .contains("idempotency_key")
                .contains("processed_at IS NULL");
    }

    @Test
    void claimRequiresEventIdOrIdempotencyKey() {
        assertThatThrownBy(() -> service.claimIfNew("tenant-1", "workspace-1", "webhook.triggered", " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId or idempotencyKey");
    }
}
