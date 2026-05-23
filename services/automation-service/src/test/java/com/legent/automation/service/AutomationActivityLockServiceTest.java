package com.legent.automation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationActivityLockServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void acquireReturnsLockedWhenActiveLockExistsWithoutOverride() {
        AutomationActivityLockService service = new AutomationActivityLockService(jdbc);
        Instant lockedUntil = Instant.now().plusSeconds(120);
        when(jdbc.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of(
                        "id", "lock-1",
                        "run_id", "run-1",
                        "locked_until", lockedUntil)));

        AutomationActivityLockService.LockLease lease = service.acquire(
                "tenant-1",
                "workspace-1",
                "activity-1",
                "run-2",
                false,
                null,
                Duration.ofMinutes(15));

        assertThat(lease.acquired()).isFalse();
        assertThat(lease.runId()).isEqualTo("run-1");
        assertThat(lease.retryAfterSeconds()).isPositive();
        verify(jdbc, never()).update(contains("INSERT INTO automation_activity_locks"), ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    void acquireRequiresOverrideReasonBeforeReplacingActiveLock() {
        AutomationActivityLockService service = new AutomationActivityLockService(jdbc);
        when(jdbc.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of(
                        "id", "lock-1",
                        "run_id", "run-1",
                        "locked_until", Instant.now().plusSeconds(120))));

        AutomationActivityLockService.LockLease lease = service.acquire(
                "tenant-1",
                "workspace-1",
                "activity-1",
                "run-2",
                true,
                " ",
                Duration.ofMinutes(15));

        assertThat(lease.acquired()).isFalse();
        assertThat(lease.reason()).contains("requires a nonblank reason");
        verify(jdbc, never()).update(contains("OVERRIDDEN"), ArgumentMatchers.<Map<String, Object>>any());
        verify(jdbc, never()).update(contains("INSERT INTO automation_activity_locks"), ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    void acquireInsertsActiveLockWhenNoCurrentLockExists() {
        AutomationActivityLockService service = new AutomationActivityLockService(jdbc);
        when(jdbc.queryForList(anyString(), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of());
        when(jdbc.update(contains("INSERT INTO automation_activity_locks"), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(1);

        AutomationActivityLockService.LockLease lease = service.acquire(
                "tenant-1",
                "workspace-1",
                "activity-1",
                "run-2",
                false,
                null,
                Duration.ofMinutes(15));

        assertThat(lease.acquired()).isTrue();
        assertThat(lease.runId()).isEqualTo("run-2");
        assertThat(lease.lockedUntil()).isAfter(Instant.now());
        verify(jdbc).update(contains("INSERT INTO automation_activity_locks"), ArgumentMatchers.<Map<String, Object>>any());
    }
}
