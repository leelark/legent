package com.legent.foundation.event;

import com.legent.foundation.domain.Tenant;
import com.legent.foundation.repository.TenantBootstrapStatusRepository;
import com.legent.foundation.repository.TenantRepository;
import com.legent.foundation.service.TenantBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantBootstrapInitializerTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private TenantBootstrapStatusRepository bootstrapStatusRepository;
    @Mock private TenantBootstrapService tenantBootstrapService;

    private TenantBootstrapInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new TenantBootstrapInitializer(
                tenantRepository,
                bootstrapStatusRepository,
                tenantBootstrapService);
    }

    @Test
    void initializeBootstrapStatusPagesActiveTenantsInDeterministicOrder() {
        PageRequest firstPage = PageRequest.of(0, TenantBootstrapInitializer.BOOTSTRAP_PAGE_SIZE);
        PageRequest secondPage = PageRequest.of(1, TenantBootstrapInitializer.BOOTSTRAP_PAGE_SIZE);
        Tenant tenant1 = tenant("tenant-1", "Acme", "acme");
        Tenant tenant2 = tenant("tenant-2", "Beta", "beta");
        Tenant tenant3 = tenant("tenant-3", "Core", "core");

        when(tenantRepository.findByStatusAndDeletedAtIsNullOrderByIdAsc(Tenant.TenantStatus.ACTIVE, firstPage))
                .thenReturn(new SliceImpl<>(List.of(tenant1, tenant2), firstPage, true));
        when(tenantRepository.findByStatusAndDeletedAtIsNullOrderByIdAsc(Tenant.TenantStatus.ACTIVE, secondPage))
                .thenReturn(new SliceImpl<>(List.of(tenant3), secondPage, false));
        when(bootstrapStatusRepository.existsById("tenant-1")).thenReturn(false);
        when(bootstrapStatusRepository.existsById("tenant-2")).thenReturn(false);
        when(bootstrapStatusRepository.existsById("tenant-3")).thenReturn(false);

        initializer.initializeBootstrapStatus();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(tenantRepository, never()).findAll();
        verify(tenantRepository, times(2)).findByStatusAndDeletedAtIsNullOrderByIdAsc(
                org.mockito.ArgumentMatchers.eq(Tenant.TenantStatus.ACTIVE),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getAllValues())
                .extracting(Pageable::getPageNumber, Pageable::getPageSize)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(0, TenantBootstrapInitializer.BOOTSTRAP_PAGE_SIZE),
                        org.assertj.core.api.Assertions.tuple(1, TenantBootstrapInitializer.BOOTSTRAP_PAGE_SIZE));

        InOrder inOrder = inOrder(tenantBootstrapService);
        inOrder.verify(tenantBootstrapService).requestBootstrap("tenant-1", "Acme", "acme", false);
        inOrder.verify(tenantBootstrapService).requestBootstrap("tenant-2", "Beta", "beta", false);
        inOrder.verify(tenantBootstrapService).requestBootstrap("tenant-3", "Core", "core", false);
    }

    @Test
    void initializeBootstrapStatusSkipsAlreadyBootstrappedTenants() {
        PageRequest page = PageRequest.of(0, TenantBootstrapInitializer.BOOTSTRAP_PAGE_SIZE);
        Tenant bootstrapped = tenant("tenant-1", "Acme", "acme");
        Tenant missing = tenant("tenant-2", "Beta", "beta");

        when(tenantRepository.findByStatusAndDeletedAtIsNullOrderByIdAsc(Tenant.TenantStatus.ACTIVE, page))
                .thenReturn(new SliceImpl<>(List.of(bootstrapped, missing), page, false));
        when(bootstrapStatusRepository.existsById("tenant-1")).thenReturn(true);
        when(bootstrapStatusRepository.existsById("tenant-2")).thenReturn(false);

        initializer.initializeBootstrapStatus();

        verify(tenantRepository, never()).findAll();
        verify(tenantBootstrapService, never()).requestBootstrap("tenant-1", "Acme", "acme", false);
        verify(tenantBootstrapService).requestBootstrap("tenant-2", "Beta", "beta", false);
    }

    private Tenant tenant(String id, String name, String slug) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setName(name);
        tenant.setSlug(slug);
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        return tenant;
    }
}
