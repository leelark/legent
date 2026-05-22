package com.legent.deliverability.service;

import com.legent.deliverability.domain.ReputationScore;
import com.legent.deliverability.repository.ReputationScoreRepository;
import com.legent.security.TenantContext;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReputationServiceTest {

    @Mock private ReputationScoreRepository repository;

    private ReputationService service;

    @BeforeEach
    void setUp() {
        service = new ReputationService(repository);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updateReputationReadsAndWritesTenantWorkspaceScopedRows() {
        ReputationScore latest = score("tenant-1", "workspace-1", "example.com", 70, Instant.parse("2026-05-21T10:00:00Z"));
        when(repository.findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                "tenant-1", "workspace-1", "example.com"))
                .thenReturn(latest);
        when(repository.save(org.mockito.ArgumentMatchers.any(ReputationScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReputationScore result = service.updateReputation(" Example.COM ", -15);

        ArgumentCaptor<ReputationScore> captor = ArgumentCaptor.forClass(ReputationScore.class);
        verify(repository).save(captor.capture());
        assertThat(result).isSameAs(captor.getValue());
        assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-1");
        assertThat(captor.getValue().getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(captor.getValue().getDomain()).isEqualTo("example.com");
        assertThat(captor.getValue().getScore()).isEqualTo(55);
    }

    @Test
    void updateReputationUsesScopedDefaultWhenNoPriorScoreExists() {
        when(repository.findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                "tenant-1", "workspace-1", "example.com"))
                .thenReturn(null);
        when(repository.save(org.mockito.ArgumentMatchers.any(ReputationScore.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReputationScore result = service.updateReputation("example.com", 30);

        assertThat(result.getTenantId()).isEqualTo("tenant-1");
        assertThat(result.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(result.getScore()).isEqualTo(100);
    }

    @Test
    void missingWorkspaceContextFailsClosedBeforeRepositoryAccess() {
        TenantContext.setWorkspaceId(null);

        assertThatThrownBy(() -> service.updateReputation("example.com", -5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");

        verifyNoInteractions(repository);
    }

    private ReputationScore score(String tenantId, String workspaceId, String domain, double score, Instant lastUpdated) {
        ReputationScore reputationScore = new ReputationScore();
        reputationScore.setTenantId(tenantId);
        reputationScore.setWorkspaceId(workspaceId);
        reputationScore.setDomain(domain);
        reputationScore.setScore(score);
        reputationScore.setLastUpdated(lastUpdated);
        return reputationScore;
    }
}
