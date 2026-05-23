package com.legent.deliverability.controller;

import com.legent.common.exception.NotFoundException;
import com.legent.deliverability.repository.SenderDomainRepository;
import com.legent.deliverability.service.DomainVerificationService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainControllerTest {

    @Mock private SenderDomainRepository domainRepository;
    @Mock private DomainVerificationService domainVerificationService;

    private DomainController controller;

    @BeforeEach
    void setUp() {
        controller = new DomainController(domainRepository, domainVerificationService);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void regenerateChallenge_failsCrossWorkspaceLookupBeforeServiceSideEffects() {
        when(domainRepository.findByTenantIdAndWorkspaceIdAndId("tenant-1", "workspace-1", "domain-2"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.regenerateChallenge("domain-2"))
                .isInstanceOf(NotFoundException.class);

        verify(domainRepository).findByTenantIdAndWorkspaceIdAndId("tenant-1", "workspace-1", "domain-2");
        verifyNoInteractions(domainVerificationService);
    }

    @Test
    void verifyDomain_failsCrossWorkspaceLookupBeforeServiceSideEffects() {
        when(domainRepository.findByTenantIdAndWorkspaceIdAndId("tenant-1", "workspace-1", "domain-2"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.verifyDomain("domain-2"))
                .isInstanceOf(NotFoundException.class);

        verify(domainRepository).findByTenantIdAndWorkspaceIdAndId("tenant-1", "workspace-1", "domain-2");
        verifyNoInteractions(domainVerificationService);
    }
}
