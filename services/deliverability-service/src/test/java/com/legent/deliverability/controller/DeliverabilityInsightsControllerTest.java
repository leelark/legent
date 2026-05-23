package com.legent.deliverability.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.deliverability.domain.DomainReputation;
import com.legent.deliverability.repository.DomainReputationRepository;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.deliverability.service.DomainVerificationService;
import com.legent.deliverability.service.PredictiveDeliverabilityService;
import com.legent.deliverability.service.SpamScoringEngine;
import com.legent.security.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverabilityInsightsControllerTest {

    @Mock private SenderDomainRepository senderDomainRepository;
    @Mock private DomainReputationRepository domainReputationRepository;
    @Mock private SuppressionListRepository suppressionListRepository;
    @Mock private SpamScoringEngine spamScoringEngine;
    @Mock private PredictiveDeliverabilityService predictiveDeliverabilityService;
    @Mock private DomainVerificationService domainVerificationService;

    private DeliverabilityInsightsController controller;

    @BeforeEach
    void setUp() {
        controller = new DeliverabilityInsightsController(
                senderDomainRepository,
                domainReputationRepository,
                suppressionListRepository,
                spamScoringEngine,
                predictiveDeliverabilityService,
                domainVerificationService);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void reputationTelemetryUsesTenantWorkspaceScopedDefaultFirstPage() {
        DomainReputation reputation = reputation("domain-1", 82);
        when(domainReputationRepository.findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(
                "tenant-1", "workspace-1", PageRequest.of(0, AppConstants.DEFAULT_PAGE_SIZE)))
                .thenReturn(List.of(reputation));

        ApiResponse<List<Map<String, Object>>> response = controller.reputationTelemetry(null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0))
                .containsEntry("domainId", "domain-1")
                .containsEntry("reputationScore", 82)
                .containsEntry("source", "DOMAIN_REPUTATION");
        verify(domainReputationRepository).findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(
                "tenant-1", "workspace-1", PageRequest.of(0, AppConstants.DEFAULT_PAGE_SIZE));
        verify(domainReputationRepository, never()).findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(
                "tenant-1", "workspace-1");
    }

    @Test
    void reputationTelemetryCapsRequestedLimitToMaxFirstPage() {
        when(domainReputationRepository.findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(
                "tenant-1", "workspace-1", PageRequest.of(0, AppConstants.MAX_PAGE_SIZE)))
                .thenReturn(List.of());

        controller.reputationTelemetry(AppConstants.MAX_PAGE_SIZE + 1);

        verify(domainReputationRepository).findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(
                "tenant-1", "workspace-1", PageRequest.of(0, AppConstants.MAX_PAGE_SIZE));
    }

    @Test
    void inboxRiskCapsReputationLimitAndPreservesMapShape() {
        DomainReputation reputation = reputation("domain-1", 80);
        when(domainReputationRepository.findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(
                "tenant-1", "workspace-1", PageRequest.of(0, AppConstants.MAX_PAGE_SIZE)))
                .thenReturn(List.of(reputation));
        when(senderDomainRepository.findByTenantIdAndWorkspaceId("tenant-1", "workspace-1"))
                .thenReturn(List.of());
        when(suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "COMPLAINT"))
                .thenReturn(0L);
        when(suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "HARD_BOUNCE"))
                .thenReturn(0L);
        when(spamScoringEngine.calculateSpamScore("hello", "<html><body>unsubscribe</body></html>"))
                .thenReturn(0);

        ApiResponse<Map<String, Object>> response = controller.inboxRisk(
                null,
                "hello",
                "<html><body>unsubscribe</body></html>",
                AppConstants.MAX_PAGE_SIZE + 1);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData())
                .containsKeys("riskScore", "riskBand", "authRisk", "contentRisk", "linkRisk",
                        "avgReputation", "complaints", "hardBounces", "breakdown", "recommendedActions",
                        "calculatedAt")
                .containsEntry("avgReputation", 80L);
        verify(domainReputationRepository).findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(
                "tenant-1", "workspace-1", PageRequest.of(0, AppConstants.MAX_PAGE_SIZE));
        verify(domainReputationRepository, never()).findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc(
                "tenant-1", "workspace-1");
    }

    private DomainReputation reputation(String domainId, int score) {
        DomainReputation reputation = new DomainReputation();
        reputation.setTenantId("tenant-1");
        reputation.setWorkspaceId("workspace-1");
        reputation.setDomainId(domainId);
        reputation.setReputationScore(score);
        reputation.setHardBounceRate(BigDecimal.valueOf(0.01));
        reputation.setComplaintRate(BigDecimal.valueOf(0.001));
        reputation.setCalculatedAt(Instant.parse("2026-05-21T10:15:30Z"));
        return reputation;
    }
}
