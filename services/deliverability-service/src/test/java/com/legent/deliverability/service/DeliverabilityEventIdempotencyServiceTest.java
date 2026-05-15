package com.legent.deliverability.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverabilityEventIdempotencyServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private DeliverabilityEventIdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new DeliverabilityEventIdempotencyService(jdbcTemplate);
    }

    @Test
    void claimIfNew_InsertsPendingClaimWithoutProcessedAt() {
        when(jdbcTemplate.update(anyString(), any(), eq("tenant-1"), eq("workspace-1"),
                eq("email.bounced"), eq("evt-1"), eq("idem-1"))).thenReturn(1);

        assertTrue(service.claimIfNew("tenant-1", "workspace-1", "email.bounced", "evt-1", "idem-1"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(), eq("tenant-1"), eq("workspace-1"),
                eq("email.bounced"), eq("evt-1"), eq("idem-1"));
        assertTrue(sqlCaptor.getValue().contains("NULL, NOW(), NOW(), 0"));
    }

    @Test
    void claimIfNew_ReturnsFalseForDuplicateClaim() {
        when(jdbcTemplate.update(anyString(), any(), eq("tenant-1"), eq("workspace-1"),
                eq("email.bounced"), eq("evt-1"), eq("idem-1"))).thenReturn(0);

        assertFalse(service.claimIfNew("tenant-1", "workspace-1", "email.bounced", "evt-1", "idem-1"));
    }

    @Test
    void markProcessed_OnlyMarksPendingClaim() {
        when(jdbcTemplate.update(anyString(), eq("tenant-1"), eq("workspace-1"),
                eq("email.bounced"), eq("evt-1"))).thenReturn(1);

        service.markProcessed("tenant-1", "workspace-1", "email.bounced", "evt-1", "idem-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), eq("tenant-1"), eq("workspace-1"),
                eq("email.bounced"), eq("evt-1"));
        assertTrue(sqlCaptor.getValue().contains("SET processed_at = NOW()"));
        assertTrue(sqlCaptor.getValue().contains("AND processed_at IS NULL"));
    }

    @Test
    void releaseClaim_DeletesOnlyPendingClaim() {
        when(jdbcTemplate.update(anyString(), eq("tenant-1"), eq("workspace-1"),
                eq("email.bounced"), eq("evt-1"))).thenReturn(1);

        service.releaseClaim("tenant-1", "workspace-1", "email.bounced", "evt-1", "idem-1");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), eq("tenant-1"), eq("workspace-1"),
                eq("email.bounced"), eq("evt-1"));
        assertTrue(sqlCaptor.getValue().contains("DELETE FROM deliverability_event_idempotency"));
        assertTrue(sqlCaptor.getValue().contains("AND processed_at IS NULL"));
    }

    @Test
    void claimIfNew_RejectsEventsWithoutEventOrIdempotencyKey() {
        assertThrows(IllegalArgumentException.class,
                () -> service.claimIfNew("tenant-1", "workspace-1", "email.bounced", " ", null));
    }
}
