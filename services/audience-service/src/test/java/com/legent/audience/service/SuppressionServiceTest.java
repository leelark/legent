package com.legent.audience.service;

import com.legent.audience.domain.Suppression;
import com.legent.audience.dto.SuppressionDto;
import com.legent.audience.repository.SuppressionRepository;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuppressionService Unit Tests")
class SuppressionServiceTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String WORKSPACE_ID = "ws-001";

    @Mock
    private SuppressionRepository suppressionRepository;

    @Mock
    private ContactLifecycleAuditService lifecycleAuditService;

    @InjectMocks
    private SuppressionService suppressionService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("create writes suppression lifecycle audit after scoped save")
    void createWritesSuppressionLifecycleAudit() {
        SuppressionDto.CreateRequest request = SuppressionDto.CreateRequest.builder()
                .email(" Blocked@Example.com ")
                .suppressionType("MANUAL")
                .reason("Manual block")
                .source("ADMIN")
                .build();
        when(suppressionRepository.existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "blocked@example.com", Suppression.SuppressionType.MANUAL))
                .thenReturn(false);
        when(suppressionRepository.save(org.mockito.ArgumentMatchers.any(Suppression.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        suppressionService.create(request);

        verify(lifecycleAuditService).suppressionCreated(
                org.mockito.ArgumentMatchers.any(Suppression.class), org.mockito.ArgumentMatchers.eq("SUPPRESSION_SERVICE"));
    }

    @Test
    @DisplayName("create conflict does not write suppression lifecycle audit")
    void createConflictDoesNotWriteSuppressionLifecycleAudit() {
        SuppressionDto.CreateRequest request = SuppressionDto.CreateRequest.builder()
                .email("blocked@example.com")
                .suppressionType("MANUAL")
                .build();
        when(suppressionRepository.existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "blocked@example.com", Suppression.SuppressionType.MANUAL))
                .thenReturn(true);

        assertThatThrownBy(() -> suppressionService.create(request))
                .isInstanceOf(ConflictException.class);

        verify(lifecycleAuditService, never()).suppressionCreated(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("delete soft-deletes suppression scoped to current tenant and workspace")
    void deleteSoftDeletesScopedSuppression() {
        Suppression suppression = new Suppression();
        suppression.setTenantId(TENANT_ID);
        suppression.setWorkspaceId(WORKSPACE_ID);
        suppression.setEmail("blocked@example.com");
        suppression.setSuppressionType(Suppression.SuppressionType.MANUAL);

        when(suppressionRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "supp-1"))
                .thenReturn(Optional.of(suppression));

        suppressionService.delete("supp-1");

        assertThat(suppression.isDeleted()).isTrue();
        verify(suppressionRepository).save(suppression);
        verify(lifecycleAuditService).suppressionDeleted(suppression, "SUPPRESSION_SERVICE");
    }

    @Test
    @DisplayName("delete rejects known id outside current tenant or workspace")
    void deleteRejectsSuppressionOutsideCurrentScope() {
        when(suppressionRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "foreign-suppression"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> suppressionService.delete("foreign-suppression"))
                .isInstanceOf(NotFoundException.class);

        verify(suppressionRepository, never()).findById("foreign-suppression");
        verify(suppressionRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(lifecycleAuditService, never()).suppressionDeleted(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
