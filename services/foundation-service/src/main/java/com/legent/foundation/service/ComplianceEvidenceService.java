package com.legent.foundation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.util.IdGenerator;
import com.legent.foundation.dto.ComplianceDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ComplianceEvidenceService {

    private final CorePlatformRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> recordAuditEvidence(ComplianceDto.AuditEvidenceRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = coalesce(request.getWorkspaceId(), TenantContext.getWorkspaceId());
        String previousHash = latestHash("immutable_audit_evidence", tenantId, workspaceId);
        String payloadJson = toJson(request.getPayload());
        String hash = hashChain(tenantId, workspaceId, request.getEventType(), request.getResourceType(),
                request.getResourceId(), payloadJson, previousHash);

        Map<String, Object> values = baseValues(tenantId, workspaceId);
        values.put("event_type", request.getEventType().trim().toUpperCase());
        values.put("resource_type", request.getResourceType());
        values.put("resource_id", coalesce(request.getResourceId(), null));
        values.put("actor_id", actor());
        values.put("payload", payloadJson);
        values.put("previous_hash", previousHash);
        values.put("event_hash", hash);
        values.put("hash_algorithm", "SHA-256");
        values.put("evidence_time", Instant.now());
        values.put("sealed_at", Instant.now());
        values.put("retention_category", coalesce(request.getRetentionCategory(), "STANDARD"));
        values.put("legal_hold", request.getLegalHold() != null && request.getLegalHold());
        return repository.insert("immutable_audit_evidence", values, List.of("payload"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAuditEvidence(String workspaceId, String resourceType, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", TenantContext.requireTenantId());
        params.put("workspaceId", coalesce(workspaceId, TenantContext.getWorkspaceId()));
        params.put("resourceType", blankToNull(resourceType));
        params.put("limit", Math.max(1, Math.min(limit, 500)));
        return repository.queryForList("""
                SELECT * FROM immutable_audit_evidence
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND (:resourceType IS NULL OR resource_type = :resourceType)
                  AND deleted_at IS NULL
                ORDER BY evidence_time DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public Map<String, Object> upsertRetentionPolicy(ComplianceDto.RetentionPolicyRequest request) {
        String sql = """
                INSERT INTO retention_matrix
                    (id, tenant_id, workspace_id, data_domain, resource_type, retention_days, disposition, legal_basis,
                     policy_version, active, metadata, created_at, updated_at, created_by, version)
                VALUES
                    (:id, :tenantId, :workspaceId, :dataDomain, :resourceType, :retentionDays, :disposition, :legalBasis,
                     :policyVersion, :active, CAST(:metadata AS jsonb), NOW(), NOW(), :createdBy, 0)
                ON CONFLICT (tenant_id, COALESCE(workspace_id, ''), data_domain, resource_type, policy_version)
                WHERE deleted_at IS NULL
                DO UPDATE SET
                    retention_days = EXCLUDED.retention_days,
                    disposition = EXCLUDED.disposition,
                    legal_basis = EXCLUDED.legal_basis,
                    active = EXCLUDED.active,
                    metadata = EXCLUDED.metadata,
                    updated_at = NOW(),
                    version = retention_matrix.version + 1
                RETURNING *
                """;
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", IdGenerator.newId());
        params.put("tenantId", TenantContext.requireTenantId());
        params.put("workspaceId", coalesce(request.getWorkspaceId(), TenantContext.getWorkspaceId()));
        params.put("dataDomain", request.getDataDomain().trim().toUpperCase());
        params.put("resourceType", request.getResourceType());
        params.put("retentionDays", request.getRetentionDays() == null ? 365 : request.getRetentionDays());
        params.put("disposition", coalesce(request.getDisposition(), "DELETE"));
        params.put("legalBasis", coalesce(request.getLegalBasis(), "LEGITIMATE_INTEREST"));
        params.put("policyVersion", coalesce(request.getPolicyVersion(), "v1"));
        params.put("active", request.getActive() == null || request.getActive());
        params.put("metadata", toJson(request.getMetadata()));
        params.put("createdBy", actor());
        return repository.queryForMap(sql, params);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRetentionMatrix(String workspaceId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", TenantContext.requireTenantId());
        params.put("workspaceId", coalesce(workspaceId, TenantContext.getWorkspaceId()));
        return repository.queryForList("""
                SELECT * FROM retention_matrix
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND deleted_at IS NULL
                ORDER BY data_domain, resource_type, policy_version DESC
                """, params);
    }

    @Transactional
    public Map<String, Object> recordConsent(ComplianceDto.ConsentLedgerRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = coalesce(request.getWorkspaceId(), TenantContext.getWorkspaceId());
        String previousHash = latestHash("consent_ledger", tenantId, workspaceId);
        String payloadJson = toJson(Map.of(
                "subjectId", request.getSubjectId(),
                "email", coalesce(request.getEmail(), ""),
                "channel", request.getChannel(),
                "purpose", request.getPurpose(),
                "status", request.getStatus(),
                "source", coalesce(request.getSource(), ""),
                "metadata", request.getMetadata() == null ? Collections.emptyMap() : request.getMetadata()
        ));
        String eventHash = hashChain(tenantId, workspaceId, request.getSubjectId(), request.getChannel(), request.getPurpose(), payloadJson, previousHash);
        Map<String, Object> values = baseValues(tenantId, workspaceId);
        values.put("subject_id", request.getSubjectId());
        values.put("email", blankToNull(request.getEmail()));
        values.put("channel", request.getChannel().trim().toUpperCase());
        values.put("purpose", request.getPurpose());
        values.put("status", request.getStatus().trim().toUpperCase());
        values.put("source", coalesce(request.getSource(), "UNKNOWN"));
        values.put("evidence_ref", blankToNull(request.getEvidenceRef()));
        values.put("occurred_at", request.getOccurredAt() == null ? Instant.now() : request.getOccurredAt());
        values.put("metadata", toJson(request.getMetadata()));
        values.put("previous_hash", previousHash);
        values.put("event_hash", eventHash);
        return repository.insert("consent_ledger", values, List.of("metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listConsentLedger(String workspaceId, String subjectId, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", TenantContext.requireTenantId());
        params.put("workspaceId", coalesce(workspaceId, TenantContext.getWorkspaceId()));
        params.put("subjectId", blankToNull(subjectId));
        params.put("limit", Math.max(1, Math.min(limit, 500)));
        return repository.queryForList("""
                SELECT * FROM consent_ledger
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND (:subjectId IS NULL OR subject_id = :subjectId)
                  AND deleted_at IS NULL
                ORDER BY occurred_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public Map<String, Object> createPrivacyRequest(ComplianceDto.PrivacyRequest request) {
        Map<String, Object> values = baseValues(TenantContext.requireTenantId(), coalesce(request.getWorkspaceId(), TenantContext.getWorkspaceId()));
        values.put("subject_id", request.getSubjectId());
        values.put("email", blankToNull(request.getEmail()));
        values.put("request_type", request.getRequestType().trim().toUpperCase());
        values.put("status", "OPEN");
        values.put("due_at", request.getDueAt());
        values.put("requested_at", Instant.now());
        values.put("completed_at", null);
        values.put("assigned_to", blankToNull(request.getAssignedTo()));
        values.put("evidence", toJson(request.getEvidence()));
        values.put("result_uri", null);
        values.put("notes", blankToNull(request.getNotes()));
        return repository.insert("privacy_requests", values, List.of("evidence"));
    }

    @Transactional
    public Map<String, Object> updatePrivacyRequest(String id, ComplianceDto.PrivacyStatusRequest request) {
        String status = request.getStatus().trim().toUpperCase();
        Map<String, Object> updated = repository.updateById(
                "privacy_requests",
                id,
                TenantContext.requireTenantId(),
                mapWithNullable(
                        "status", status,
                        "completed_at", List.of("COMPLETED", "REJECTED", "CANCELLED").contains(status) ? Instant.now() : null,
                        "result_uri", blankToNull(request.getResultUri()),
                        "evidence", toJson(request.getEvidence()),
                        "notes", blankToNull(request.getNotes())
                ),
                List.of("evidence")
        );
        recordAuditEvidence(evidenceForPrivacyStatus(updated));
        return updated;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPrivacyRequests(String workspaceId, String status, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", TenantContext.requireTenantId());
        params.put("workspaceId", coalesce(workspaceId, TenantContext.getWorkspaceId()));
        params.put("status", blankToNull(status) == null ? null : status.trim().toUpperCase());
        params.put("limit", Math.max(1, Math.min(limit, 500)));
        return repository.queryForList("""
                SELECT * FROM privacy_requests
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND (:status IS NULL OR status = :status)
                  AND deleted_at IS NULL
                ORDER BY requested_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public Map<String, Object> createComplianceExport(ComplianceDto.ComplianceExportRequest request) {
        String exportType = request.getExportType().trim().toUpperCase();
        String format = coalesce(request.getFormat(), "JSON").trim().toUpperCase();
        Map<String, Object> values = baseValues(TenantContext.requireTenantId(), coalesce(request.getWorkspaceId(), TenantContext.getWorkspaceId()));
        values.put("export_type", exportType);
        values.put("status", "COMPLETED");
        values.put("format", format);
        values.put("requested_by", actor());
        values.put("filters", toJson(request.getFilters()));
        values.put("result_uri", "compliance-export://" + exportType.toLowerCase() + "/" + values.get("id"));
        values.put("row_count", estimateRows(exportType, values.get("workspace_id")));
        values.put("requested_at", Instant.now());
        values.put("completed_at", Instant.now());
        values.put("error_message", null);
        Map<String, Object> saved = repository.insert("compliance_export_jobs", values, List.of("filters"));
        recordAuditEvidence(evidenceForExport(saved));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listComplianceExports(String workspaceId, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", TenantContext.requireTenantId());
        params.put("workspaceId", coalesce(workspaceId, TenantContext.getWorkspaceId()));
        params.put("limit", Math.max(1, Math.min(limit, 500)));
        return repository.queryForList("""
                SELECT * FROM compliance_export_jobs
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND deleted_at IS NULL
                ORDER BY requested_at DESC
                LIMIT :limit
                """, params);
    }

    public String hashChain(String tenantId, String workspaceId, String partA, String partB, String partC, String payloadJson, String previousHash) {
        return sha256(String.join("|",
                coalesce(tenantId, ""),
                coalesce(workspaceId, ""),
                coalesce(partA, ""),
                coalesce(partB, ""),
                coalesce(partC, ""),
                coalesce(payloadJson, "{}"),
                coalesce(previousHash, "GENESIS")));
    }

    private String latestHash(String table, String tenantId, String workspaceId) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", tenantId);
        params.put("workspaceId", workspaceId);
        List<Map<String, Object>> rows = repository.queryForList(
                "SELECT event_hash FROM " + table + " WHERE tenant_id = :tenantId AND (:workspaceId IS NULL OR workspace_id = :workspaceId) AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1",
                params
        );
        return rows.isEmpty() ? null : String.valueOf(rows.get(0).get("event_hash"));
    }

    private long estimateRows(String exportType, Object workspaceId) {
        String table = switch (exportType) {
            case "AUDIT_EVIDENCE" -> "immutable_audit_evidence";
            case "CONSENT_LEDGER" -> "consent_ledger";
            case "PRIVACY_REQUESTS" -> "privacy_requests";
            case "RETENTION_MATRIX" -> "retention_matrix";
            default -> "compliance_export_jobs";
        };
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("tenantId", TenantContext.requireTenantId());
        params.put("workspaceId", workspaceId);
        return repository.queryForList(
                "SELECT COUNT(*) AS count FROM " + table + " WHERE tenant_id = :tenantId AND (:workspaceId IS NULL OR workspace_id = :workspaceId) AND deleted_at IS NULL",
                params
        ).stream().findFirst().map(row -> ((Number) row.get("count")).longValue()).orElse(0L);
    }

    private ComplianceDto.AuditEvidenceRequest evidenceForPrivacyStatus(Map<String, Object> updated) {
        ComplianceDto.AuditEvidenceRequest request = new ComplianceDto.AuditEvidenceRequest();
        request.setWorkspaceId((String) updated.get("workspace_id"));
        request.setEventType("PRIVACY_REQUEST_STATUS");
        request.setResourceType("PrivacyRequest");
        request.setResourceId(String.valueOf(updated.get("id")));
        request.setRetentionCategory("PRIVACY");
        request.setPayload(updated);
        return request;
    }

    private ComplianceDto.AuditEvidenceRequest evidenceForExport(Map<String, Object> export) {
        ComplianceDto.AuditEvidenceRequest request = new ComplianceDto.AuditEvidenceRequest();
        request.setWorkspaceId((String) export.get("workspace_id"));
        request.setEventType("COMPLIANCE_EXPORT");
        request.setResourceType("ComplianceExport");
        request.setResourceId(String.valueOf(export.get("id")));
        request.setRetentionCategory("COMPLIANCE");
        request.setPayload(export);
        return request;
    }

    private Map<String, Object> baseValues(String tenantId, String workspaceId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", IdGenerator.newId());
        values.put("tenant_id", tenantId);
        values.put("workspace_id", workspaceId);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("created_by", actor());
        values.put("deleted_at", null);
        values.put("version", 0L);
        return values;
    }

    private Map<String, Object> mapWithNullable(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private String actor() {
        String actor = TenantContext.getUserId();
        return actor == null || actor.isBlank() ? "SYSTEM" : actor;
    }

    private String toJson(Object value) {
        Object safe = value == null ? Collections.emptyMap() : value;
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize compliance payload", e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash compliance evidence", e);
        }
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
