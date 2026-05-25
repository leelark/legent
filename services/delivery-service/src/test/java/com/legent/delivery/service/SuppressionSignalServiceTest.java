package com.legent.delivery.service;

import com.legent.delivery.domain.SuppressionSignal;
import com.legent.delivery.repository.SuppressionSignalRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuppressionSignalServiceTest {

    @Mock private SuppressionSignalRepository repository;

    private SuppressionSignalService service;

    @BeforeEach
    void setUp() {
        service = new SuppressionSignalService(repository);
    }

    @Test
    void recordSignalCreatesWorkspaceScopedNormalizedSignal() {
        when(repository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndTypeAndDeletedAtIsNull(
                "tenant-1",
                "workspace-1",
                "user@example.com",
                "COMPLAINT"))
                .thenReturn(Optional.empty());
        when(repository.save(any(SuppressionSignal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SuppressionSignal signal = service.recordSignal(
                "tenant-1",
                "workspace-1",
                " User@Example.com ",
                SuppressionSignal.SignalType.COMPLAINT,
                "complaint",
                "message-1");

        assertThat(signal.getTenantId()).isEqualTo("tenant-1");
        assertThat(signal.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(signal.getOwnershipScope()).isEqualTo("WORKSPACE");
        assertThat(signal.getEmail()).isEqualTo("user@example.com");
        assertThat(signal.getType()).isEqualTo("COMPLAINT");
        assertThat(signal.getReason()).isEqualTo("complaint");
        assertThat(signal.getSourceMessageId()).isEqualTo("message-1");
        verify(repository).save(any(SuppressionSignal.class));
    }

    @Test
    void recordSignalReturnsExistingActiveSignalWithoutDuplicateWrite() {
        SuppressionSignal existing = new SuppressionSignal();
        existing.setEmail("user@example.com");
        existing.setType("HARD_BOUNCE");
        when(repository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndTypeAndDeletedAtIsNull(
                "tenant-1",
                "workspace-1",
                "user@example.com",
                "HARD_BOUNCE"))
                .thenReturn(Optional.of(existing));

        SuppressionSignal signal = service.recordSignal(
                "tenant-1",
                "workspace-1",
                "user@example.com",
                SuppressionSignal.SignalType.HARD_BOUNCE,
                "hard bounce",
                "message-1");

        assertThat(signal).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void recordSignalFailsClosedOnMissingWorkspace() {
        assertThatThrownBy(() -> service.recordSignal(
                "tenant-1",
                " ",
                "user@example.com",
                SuppressionSignal.SignalType.UNSUBSCRIBE,
                "unsubscribe",
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId");
    }
}
