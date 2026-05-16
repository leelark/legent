package com.legent.deliverability.service;

import com.legent.deliverability.domain.SenderDomain;
import com.legent.deliverability.domain.SenderDomainVerificationHistory;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.repository.SenderDomainVerificationHistoryRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainVerificationServiceTest {

    @Mock private SenderDomainRepository domainRepository;
    @Mock private SenderDomainVerificationHistoryRepository historyRepository;

    private StubDnsTxtResolver dnsTxtResolver;
    private DomainVerificationService service;

    @BeforeEach
    void setUp() {
        dnsTxtResolver = new StubDnsTxtResolver();
        service = new DomainVerificationService(domainRepository, historyRepository, dnsTxtResolver);
        ReflectionTestUtils.setField(service, "challengeTtlHours", 168L);
        ReflectionTestUtils.setField(service, "proofMaxAgeHours", 720L);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void verifyDomain_requiresExactOwnedChallengeToken() {
        SenderDomain domain = issuedDomain();
        dnsTxtResolver.records = Map.of(
                "example.com", List.of("v=spf1 include:_spf.example.net -all"),
                "legent._domainkey.example.com", List.of("v=DKIM1; k=rsa; p=abc"),
                "_dmarc.example.com", List.of("v=DMARC1; p=reject"),
                "_legent-verification.example.com", List.of("legent-domain-verification=wrong-token")
        );
        when(domainRepository.findByTenantIdAndId("tenant-1", "domain-1")).thenReturn(Optional.of(domain));
        when(domainRepository.save(any(SenderDomain.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SenderDomain result = service.verifyDomain("domain-1");

        assertThat(result.getStatus()).isEqualTo(SenderDomain.VerificationStatus.FAILED);
        assertThat(result.getIsActive()).isFalse();
        assertThat(result.getOwnershipTokenVerified()).isFalse();
        assertThat(result.getVerificationFailureReason()).contains("missing exact owned TXT challenge token");
        ArgumentCaptor<SenderDomainVerificationHistory> history = ArgumentCaptor.forClass(SenderDomainVerificationHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().isOwnershipTokenVerified()).isFalse();
    }

    @Test
    void verifyDomain_marksVerifiedOnlyWithFreshExactOwnedChallengeTokenAndAuthRecords() {
        SenderDomain domain = issuedDomain();
        dnsTxtResolver.records = Map.of(
                "example.com", List.of("v=spf1 include:_spf.example.net -all"),
                "legent._domainkey.example.com", List.of("v=DKIM1; k=rsa; p=abc"),
                "_dmarc.example.com", List.of("v=DMARC1; p=reject"),
                "_legent-verification.example.com", List.of(domain.getVerificationRecordValue())
        );
        when(domainRepository.findByTenantIdAndId("tenant-1", "domain-1")).thenReturn(Optional.of(domain));
        when(domainRepository.save(any(SenderDomain.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SenderDomain result = service.verifyDomain("domain-1");

        assertThat(result.getStatus()).isEqualTo(SenderDomain.VerificationStatus.VERIFIED);
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getOwnershipTokenVerified()).isTrue();
        assertThat(result.getOwnershipTokenVerifiedAt()).isNotNull();
        assertThat(service.hasFreshOwnershipProof(result)).isTrue();
    }

    @Test
    void verifyDomain_failsClosedWhenMockDnsIsEnabled() {
        SenderDomain domain = issuedDomain();
        ReflectionTestUtils.setField(service, "mockDns", true);
        when(domainRepository.findByTenantIdAndId("tenant-1", "domain-1")).thenReturn(Optional.of(domain));
        when(domainRepository.save(any(SenderDomain.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SenderDomain result = service.verifyDomain("domain-1");

        assertThat(result.getStatus()).isEqualTo(SenderDomain.VerificationStatus.FAILED);
        assertThat(result.getIsActive()).isFalse();
        assertThat(result.getVerificationFailureReason()).contains("owned DNS token proof is required");
    }

    private SenderDomain issuedDomain() {
        SenderDomain domain = new SenderDomain();
        domain.setId("domain-1");
        domain.setTenantId("tenant-1");
        domain.setWorkspaceId("workspace-1");
        domain.setDomainName("example.com");
        domain.setDkimSelector("legent");
        return service.issueChallenge(domain);
    }

    private static class StubDnsTxtResolver implements DnsTxtResolver {
        private Map<String, List<String>> records = Map.of();

        @Override
        public List<String> lookupTxt(String name) {
            return records.getOrDefault(name, List.of());
        }
    }
}
