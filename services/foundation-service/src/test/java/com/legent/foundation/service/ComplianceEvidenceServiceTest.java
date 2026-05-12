package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.ComplianceDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceEvidenceServiceTest {

    @Mock
    private CorePlatformRepository repository;

    private ComplianceEvidenceService service;

    @BeforeEach
    void setUp() {
        service = new ComplianceEvidenceService(repository, new ObjectMapper());
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void recordAuditEvidence_appendsHashChainFields() {
        when(repository.queryForList(any(String.class), any(Map.class))).thenReturn(List.of(Map.of("event_hash", "prev-hash")));
        when(repository.insert(eq("immutable_audit_evidence"), any(Map.class), eq(List.of("payload"))))
                .thenAnswer(invocation -> invocation.getArgument(1));

        ComplianceDto.AuditEvidenceRequest request = new ComplianceDto.AuditEvidenceRequest();
        request.setEventType("role_binding_create");
        request.setResourceType("PrincipalRoleBinding");
        request.setResourceId("binding-1");
        request.setPayload(Map.of("permission", "delivery:*"));

        Map<String, Object> saved = service.recordAuditEvidence(request);

        assertThat(saved.get("tenant_id")).isEqualTo("tenant-1");
        assertThat(saved.get("workspace_id")).isEqualTo("workspace-1");
        assertThat(saved.get("previous_hash")).isEqualTo("prev-hash");
        assertThat((String) saved.get("event_hash")).hasSize(64);
        assertThat(saved.get("event_type")).isEqualTo("ROLE_BINDING_CREATE");
    }

    @Test
    void hashChain_changesWhenPreviousHashChanges() {
        String first = service.hashChain("tenant-1", "workspace-1", "A", "B", "C", "{}", null);
        String second = service.hashChain("tenant-1", "workspace-1", "A", "B", "C", "{}", first);

        assertThat(first).hasSize(64);
        assertThat(second).hasSize(64);
        assertThat(second).isNotEqualTo(first);
    }
}
