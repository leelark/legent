package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.dto.ComplianceDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.List;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(repository.queryForList(any(String.class), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of(Map.of("event_hash", "prev-hash")));
        when(repository.insert(eq("immutable_audit_evidence"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("payload"))))
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

    @Test
    void updatePrivacyRequest_scopesMutationToCurrentWorkspaceAndRecordsAudit() {
        Map<String, Object> updated = Map.of(
                "id", "privacy-1",
                "tenant_id", "tenant-1",
                "workspace_id", "workspace-1",
                "subject_id", "subject-1",
                "email", "person@example.com",
                "request_type", "ERASURE",
                "status", "COMPLETED",
                "evidence", Map.of("ticket", "case-1")
        );
        when(repository.updateByIdAndWorkspace(
                eq("privacy_requests"),
                eq("privacy-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                ArgumentMatchers.<Map<String, Object>>any(),
                eq(List.of("evidence"))))
                .thenReturn(updated);
        when(repository.queryForList(ArgumentMatchers.contains("immutable_audit_evidence"), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of());
        when(repository.insert(eq("immutable_audit_evidence"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("payload"))))
                .thenAnswer(invocation -> invocation.getArgument(1));

        ComplianceDto.PrivacyStatusRequest request = new ComplianceDto.PrivacyStatusRequest();
        request.setStatus("completed");
        request.setResultUri("compliance-result://privacy/privacy-1");
        request.setEvidence(Map.of("ticket", "case-1"));
        request.setNotes("Completed by privacy operator");

        Map<String, Object> result = service.updatePrivacyRequest("privacy-1", request);

        assertThat(result).isSameAs(updated);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repository).updateByIdAndWorkspace(
                eq("privacy_requests"),
                eq("privacy-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                updatesCaptor.capture(),
                eq(List.of("evidence")));
        assertThat(updatesCaptor.getValue())
                .containsEntry("status", "COMPLETED")
                .containsEntry("result_uri", "compliance-result://privacy/privacy-1")
                .containsEntry("notes", "Completed by privacy operator");
        assertThat(updatesCaptor.getValue()).containsKey("completed_at");
        verify(repository, never()).updateById(eq("privacy_requests"), anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditCaptor = ArgumentCaptor.forClass(Map.class);
        verify(repository).insert(eq("immutable_audit_evidence"), auditCaptor.capture(), eq(List.of("payload")));
        assertThat(auditCaptor.getValue())
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("workspace_id", "workspace-1")
                .containsEntry("event_type", "PRIVACY_REQUEST_STATUS")
                .containsEntry("resource_type", "PrivacyRequest")
                .containsEntry("resource_id", "privacy-1")
                .containsEntry("retention_category", "PRIVACY");
    }

    @Test
    void updatePrivacyRequest_rejectsMissingWorkspaceBeforeMutation() {
        TenantContext.setWorkspaceId(null);
        ComplianceDto.PrivacyStatusRequest request = new ComplianceDto.PrivacyStatusRequest();
        request.setStatus("completed");

        assertThatThrownBy(() -> service.updatePrivacyRequest("privacy-1", request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");

        verify(repository, never()).updateById(eq("privacy_requests"), anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
        verify(repository, never()).updateByIdAndWorkspace(eq("privacy_requests"), anyString(), anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
        verify(repository, never()).insert(eq("immutable_audit_evidence"), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    @Test
    void compliancePaths_rejectMissingWorkspaceBeforeRepositoryAccess() {
        TenantContext.setWorkspaceId(null);

        assertMissingWorkspaceFails(() -> service.recordAuditEvidence(auditEvidenceRequest(null)));
        assertMissingWorkspaceFails(() -> service.listAuditEvidence(null, null, 100));
        assertMissingWorkspaceFails(() -> service.upsertRetentionPolicy(retentionPolicyRequest(null)));
        assertMissingWorkspaceFails(() -> service.listRetentionMatrix(null));
        assertMissingWorkspaceFails(() -> service.recordConsent(consentLedgerRequest(null)));
        assertMissingWorkspaceFails(() -> service.listConsentLedger(null, null, 100));
        assertMissingWorkspaceFails(() -> service.createPrivacyRequest(privacyRequest(null)));
        assertMissingWorkspaceFails(() -> service.listPrivacyRequests(null, null, 100));
        assertMissingWorkspaceFails(() -> service.createComplianceExport(complianceExportRequest(null)));
        assertMissingWorkspaceFails(() -> service.listComplianceExports(null, 100));

        verifyNoInteractions(repository);
    }

    @Test
    void compliancePaths_rejectExplicitWorkspaceMismatchBeforeRepositoryAccess() {
        assertWorkspaceMismatchFails(() -> service.recordAuditEvidence(auditEvidenceRequest("workspace-2")));
        assertWorkspaceMismatchFails(() -> service.listAuditEvidence("workspace-2", null, 100));
        assertWorkspaceMismatchFails(() -> service.upsertRetentionPolicy(retentionPolicyRequest("workspace-2")));
        assertWorkspaceMismatchFails(() -> service.listRetentionMatrix("workspace-2"));
        assertWorkspaceMismatchFails(() -> service.recordConsent(consentLedgerRequest("workspace-2")));
        assertWorkspaceMismatchFails(() -> service.listConsentLedger("workspace-2", null, 100));
        assertWorkspaceMismatchFails(() -> service.createPrivacyRequest(privacyRequest("workspace-2")));
        assertWorkspaceMismatchFails(() -> service.listPrivacyRequests("workspace-2", null, 100));
        assertWorkspaceMismatchFails(() -> service.createComplianceExport(complianceExportRequest("workspace-2")));
        assertWorkspaceMismatchFails(() -> service.listComplianceExports("workspace-2", 100));

        verifyNoInteractions(repository);
    }

    @Test
    void listQueries_useExactTrustedWorkspaceScope() {
        when(repository.queryForList(any(String.class), ArgumentMatchers.<Map<String, Object>>any())).thenReturn(List.of());

        service.listAuditEvidence("workspace-1", "PrivacyRequest", 25);
        service.listRetentionMatrix("workspace-1");
        service.listConsentLedger("workspace-1", "subject-1", 25);
        service.listPrivacyRequests("workspace-1", "open", 25);
        service.listComplianceExports("workspace-1", 25);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository, org.mockito.Mockito.times(5)).queryForList(sqlCaptor.capture(), paramsCaptor.capture());

        assertThat(sqlCaptor.getAllValues())
                .allSatisfy(sql -> assertThat(sql)
                        .contains("workspace_id = :workspaceId")
                        .doesNotContain(":workspaceId IS NULL"));
        assertThat(paramsCaptor.getAllValues())
                .allSatisfy(params -> assertThat(params).containsEntry("workspaceId", "workspace-1"));
    }

    @Test
    void createComplianceExport_usesExactTrustedWorkspaceForRowEstimateAndAudit() {
        when(repository.queryForList(any(String.class), ArgumentMatchers.<Map<String, Object>>any()))
                .thenReturn(List.of(Map.of("count", 7L)))
                .thenReturn(List.of());
        when(repository.insert(eq("compliance_export_jobs"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("filters"))))
                .thenAnswer(invocation -> {
                    Map<String, Object> values = invocation.getArgument(1);
                    return Map.of(
                            "id", values.get("id"),
                            "tenant_id", values.get("tenant_id"),
                            "workspace_id", values.get("workspace_id"),
                            "export_type", values.get("export_type"),
                            "row_count", values.get("row_count")
                    );
                });
        when(repository.insert(eq("immutable_audit_evidence"), ArgumentMatchers.<Map<String, Object>>any(), eq(List.of("payload"))))
                .thenAnswer(invocation -> invocation.getArgument(1));

        Map<String, Object> saved = service.createComplianceExport(complianceExportRequest("workspace-1"));

        assertThat(saved)
                .containsEntry("tenant_id", "tenant-1")
                .containsEntry("workspace_id", "workspace-1")
                .containsEntry("row_count", 7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository, org.mockito.Mockito.times(2)).queryForList(sqlCaptor.capture(), paramsCaptor.capture());
        assertThat(sqlCaptor.getAllValues())
                .allSatisfy(sql -> assertThat(sql)
                        .contains("workspace_id = :workspaceId")
                        .doesNotContain(":workspaceId IS NULL"));
        assertThat(paramsCaptor.getAllValues())
                .allSatisfy(params -> assertThat(params).containsEntry("workspaceId", "workspace-1"));
    }

    @Test
    void updatePrivacyRequest_doesNotAuditWhenScopedRowIsMissing() {
        when(repository.updateByIdAndWorkspace(
                eq("privacy_requests"),
                eq("privacy-other-workspace"),
                eq("tenant-1"),
                eq("workspace-1"),
                ArgumentMatchers.<Map<String, Object>>any(),
                eq(List.of("evidence"))))
                .thenThrow(new EmptyResultDataAccessException(1));

        ComplianceDto.PrivacyStatusRequest request = new ComplianceDto.PrivacyStatusRequest();
        request.setStatus("rejected");
        request.setNotes("No verified subject match");

        assertThatThrownBy(() -> service.updatePrivacyRequest("privacy-other-workspace", request))
                .isInstanceOf(EmptyResultDataAccessException.class);

        verify(repository, never()).updateById(eq("privacy_requests"), anyString(), anyString(), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
        verify(repository, never()).insert(eq("immutable_audit_evidence"), ArgumentMatchers.<Map<String, Object>>any(), ArgumentMatchers.<List<String>>any());
    }

    private void assertMissingWorkspaceFails(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace context is not set");
    }

    private void assertWorkspaceMismatchFails(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId does not match the current workspace");
    }

    private ComplianceDto.AuditEvidenceRequest auditEvidenceRequest(String workspaceId) {
        ComplianceDto.AuditEvidenceRequest request = new ComplianceDto.AuditEvidenceRequest();
        request.setWorkspaceId(workspaceId);
        request.setEventType("privacy_request_create");
        request.setResourceType("PrivacyRequest");
        request.setResourceId("privacy-1");
        request.setPayload(Map.of("status", "OPEN"));
        return request;
    }

    private ComplianceDto.RetentionPolicyRequest retentionPolicyRequest(String workspaceId) {
        ComplianceDto.RetentionPolicyRequest request = new ComplianceDto.RetentionPolicyRequest();
        request.setWorkspaceId(workspaceId);
        request.setDataDomain("privacy");
        request.setResourceType("PrivacyRequest");
        request.setRetentionDays(365);
        return request;
    }

    private ComplianceDto.ConsentLedgerRequest consentLedgerRequest(String workspaceId) {
        ComplianceDto.ConsentLedgerRequest request = new ComplianceDto.ConsentLedgerRequest();
        request.setWorkspaceId(workspaceId);
        request.setSubjectId("subject-1");
        request.setEmail("person@example.com");
        request.setChannel("email");
        request.setPurpose("marketing");
        request.setStatus("granted");
        return request;
    }

    private ComplianceDto.PrivacyRequest privacyRequest(String workspaceId) {
        ComplianceDto.PrivacyRequest request = new ComplianceDto.PrivacyRequest();
        request.setWorkspaceId(workspaceId);
        request.setSubjectId("subject-1");
        request.setEmail("person@example.com");
        request.setRequestType("erasure");
        return request;
    }

    private ComplianceDto.ComplianceExportRequest complianceExportRequest(String workspaceId) {
        ComplianceDto.ComplianceExportRequest request = new ComplianceDto.ComplianceExportRequest();
        request.setWorkspaceId(workspaceId);
        request.setExportType("audit_evidence");
        request.setFormat("json");
        request.setFilters(Map.of("resourceType", "PrivacyRequest"));
        return request;
    }
}
