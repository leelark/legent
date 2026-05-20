package com.legent.foundation.service;

import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.Tenant;
import com.legent.foundation.dto.TenantDto;
import com.legent.foundation.mapper.TenantMapper;
import com.legent.foundation.repository.TenantRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private AuditService auditService;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, tenantMapper, auditService);
        TenantContext.setTenantId("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getTenant_returnsCurrentTenant() {
        Tenant tenant = new Tenant();
        tenant.setId("tenant-1");
        tenant.setName("Acme");
        TenantDto.Response response = TenantDto.Response.builder()
                .id("tenant-1")
                .name("Acme")
                .build();

        when(tenantRepository.findActiveById("tenant-1")).thenReturn(Optional.of(tenant));
        when(tenantMapper.toResponse(tenant)).thenReturn(response);

        TenantDto.Response result = tenantService.getTenant("tenant-1");

        assertThat(result.getId()).isEqualTo("tenant-1");
        verify(tenantRepository).findActiveById("tenant-1");
        verify(tenantMapper).toResponse(tenant);
    }

    @Test
    void getTenant_hidesCrossTenantBeforeRepositoryLookup() {
        assertThatThrownBy(() -> tenantService.getTenant("tenant-2"))
                .isInstanceOf(NotFoundException.class);

        verify(tenantRepository, never()).findActiveById("tenant-2");
        verifyNoInteractions(tenantMapper);
    }

    @Test
    void getTenant_missingTenantContextFailsBeforeRepositoryLookup() {
        TenantContext.clear();

        assertThatThrownBy(() -> tenantService.getTenant("tenant-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context is not set");

        verifyNoInteractions(tenantRepository, tenantMapper);
    }
}
