package com.legent.deliverability.service;

import com.legent.deliverability.domain.DomainReputation;
import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.repository.DomainReputationRepository;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictiveDeliverabilityServiceTest {

    @Mock private SenderDomainRepository senderDomainRepository;
    @Mock private DomainReputationRepository domainReputationRepository;
    @Mock private SuppressionListRepository suppressionListRepository;
    @Mock private DomainVerificationService domainVerificationService;

    private PredictiveDeliverabilityService service;

    @BeforeEach
    void setUp() {
        service = new PredictiveDeliverabilityService(senderDomainRepository, domainReputationRepository, suppressionListRepository, domainVerificationService);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void predictRisk_flagsWarmupAndRemediationForWeakReputation() {
        SenderDomain domain = new SenderDomain();
        domain.setId("domain-1");
        domain.setDomainName("example.com");
        domain.setStatus(SenderDomain.VerificationStatus.PENDING);
        domain.setSpfVerified(false);
        domain.setDkimVerified(true);
        domain.setDmarcVerified(false);

        DomainReputation reputation = new DomainReputation();
        reputation.setDomainId("domain-1");
        reputation.setReputationScore(58);
        reputation.setComplaintRate(BigDecimal.valueOf(0.004));
        reputation.setHardBounceRate(BigDecimal.valueOf(0.06));
        reputation.setCalculatedAt(Instant.now());

        when(senderDomainRepository.findByTenantIdAndWorkspaceId("tenant-1", "workspace-1")).thenReturn(List.of(domain));
        when(domainReputationRepository.findByTenantIdAndWorkspaceIdOrderByCalculatedAtDesc("tenant-1", "workspace-1")).thenReturn(List.of(reputation));
        when(suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "COMPLAINT")).thenReturn(15L);
        when(suppressionListRepository.countByTenantIdAndWorkspaceIdAndReason("tenant-1", "workspace-1", "HARD_BOUNCE")).thenReturn(60L);

        Map<String, Object> prediction = service.predictRisk("example.com", 50_000, "gmail");

        assertThat(prediction.get("riskBand")).isEqualTo("HIGH");
        assertThat(prediction.get("warmupPhase")).isEqualTo("REMEDIATE_AND_RAMP");
        assertThat((Integer) prediction.get("recommendedDailyCap")).isLessThanOrEqualTo(1_000);
        assertThat((List<?>) prediction.get("remediationGuidance")).isNotEmpty();
    }
}
