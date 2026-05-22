package com.legent.deliverability.controller;

import com.legent.deliverability.domain.DomainReputation;
import com.legent.deliverability.domain.ReputationScore;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.DomainReputationRepository;
import com.legent.deliverability.repository.ReputationScoreRepository;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.security.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReputationControllerTest {

    @Mock private DomainReputationRepository domainReputationRepository;
    @Mock private SenderDomainRepository senderDomainRepository;
    @Mock private ReputationScoreRepository reputationScoreRepository;

    private ReputationController controller;

    @BeforeEach
    void setUp() {
        controller = new ReputationController(domainReputationRepository, senderDomainRepository, reputationScoreRepository);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void legacyFallbackUsesTenantWorkspaceAndNormalizedDomain() {
        ReputationScore score = reputationScore("tenant-1", "workspace-1", "example.com", 72, Instant.parse("2026-05-21T10:15:30Z"));
        when(senderDomainRepository.findByTenantIdAndWorkspaceIdAndDomainName("tenant-1", "workspace-1", "example.com"))
                .thenReturn(Optional.empty());
        when(reputationScoreRepository.findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                "tenant-1", "workspace-1", "example.com"))
                .thenReturn(score);

        ResponseEntity<Map<String, Object>> response = controller.getScoreByDomain(" Example.COM ");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("domain", "example.com")
                .containsEntry("score", 72.0)
                .containsEntry("source", "LEGACY_REPUTATION_SCORE");
        verify(reputationScoreRepository).findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                "tenant-1", "workspace-1", "example.com");
    }

    @Test
    void legacyFallbackDoesNotUseDomainOnlyLookupForAnotherWorkspace() {
        when(senderDomainRepository.findByTenantIdAndWorkspaceIdAndDomainName("tenant-1", "workspace-1", "example.com"))
                .thenReturn(Optional.empty());
        when(reputationScoreRepository.findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                "tenant-1", "workspace-1", "example.com"))
                .thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.getScoreByDomain("example.com");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        verify(reputationScoreRepository).findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                "tenant-1", "workspace-1", "example.com");
    }

    @Test
    void senderDomainWithoutCurrentReputationFallsBackToScopedLegacyScore() {
        SenderDomain senderDomain = new SenderDomain();
        senderDomain.setId("domain-1");
        senderDomain.setTenantId("tenant-1");
        senderDomain.setWorkspaceId("workspace-1");
        senderDomain.setDomainName("example.com");
        ReputationScore legacyScore = reputationScore("tenant-1", "workspace-1", "example.com", 64, Instant.parse("2026-05-21T10:15:30Z"));

        when(senderDomainRepository.findByTenantIdAndWorkspaceIdAndDomainName("tenant-1", "workspace-1", "example.com"))
                .thenReturn(Optional.of(senderDomain));
        when(domainReputationRepository.findByTenantIdAndWorkspaceIdAndDomainId("tenant-1", "workspace-1", "domain-1"))
                .thenReturn(Optional.empty());
        when(reputationScoreRepository.findTopByTenantIdAndWorkspaceIdAndDomainOrderByLastUpdatedDesc(
                "tenant-1", "workspace-1", "example.com"))
                .thenReturn(legacyScore);

        ResponseEntity<Map<String, Object>> response = controller.getScoreByDomain("example.com");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("source", "LEGACY_REPUTATION_SCORE");
    }

    @Test
    void currentDomainReputationTakesPrecedenceOverLegacyScore() {
        SenderDomain senderDomain = new SenderDomain();
        senderDomain.setId("domain-1");
        senderDomain.setTenantId("tenant-1");
        senderDomain.setWorkspaceId("workspace-1");
        senderDomain.setDomainName("example.com");
        DomainReputation reputation = new DomainReputation();
        reputation.setTenantId("tenant-1");
        reputation.setWorkspaceId("workspace-1");
        reputation.setDomainId("domain-1");
        reputation.setReputationScore(91);
        reputation.setCalculatedAt(Instant.parse("2026-05-21T10:15:30Z"));

        when(senderDomainRepository.findByTenantIdAndWorkspaceIdAndDomainName("tenant-1", "workspace-1", "example.com"))
                .thenReturn(Optional.of(senderDomain));
        when(domainReputationRepository.findByTenantIdAndWorkspaceIdAndDomainId("tenant-1", "workspace-1", "domain-1"))
                .thenReturn(Optional.of(reputation));

        ResponseEntity<Map<String, Object>> response = controller.getScoreByDomain("example.com");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("score", 91)
                .containsEntry("source", "DOMAIN_REPUTATION");
        verifyNoInteractions(reputationScoreRepository);
    }

    private ReputationScore reputationScore(String tenantId, String workspaceId, String domain, double score, Instant lastUpdated) {
        ReputationScore reputationScore = new ReputationScore();
        reputationScore.setTenantId(tenantId);
        reputationScore.setWorkspaceId(workspaceId);
        reputationScore.setDomain(domain);
        reputationScore.setScore(score);
        reputationScore.setLastUpdated(lastUpdated);
        return reputationScore;
    }
}
