package com.legent.foundation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.common.util.IdGenerator;
import com.legent.foundation.dto.GlobalEnterpriseDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GlobalEnterpriseService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final List<String> TOPOLOGY_MODES = List.of("ACTIVE_WARM", "ACTIVE_ACTIVE");
    private static final List<String> OPTIMIZATION_MODES = List.of("SUGGEST_ONLY", "AUTO_APPLY_WITH_GUARDRAILS");

    private final CorePlatformRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> upsertOperatingModel(GlobalEnterpriseDto.OperatingModelRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        String topology = normalize(defaultValue(request.getTopologyMode(), "ACTIVE_WARM"));
        if (!TOPOLOGY_MODES.contains(topology)) {
            throw new IllegalArgumentException("topologyMode must be ACTIVE_WARM or ACTIVE_ACTIVE");
        }
        List<String> standbyRegions = normalizedList(request.getStandbyRegions());
        List<String> activeRegions = normalizedList(request.getActiveRegions());
        String primaryRegion = normalizeRegion(request.getPrimaryRegion());
        if ("ACTIVE_ACTIVE".equals(topology) && activeRegions.isEmpty()) {
            activeRegions = new ArrayList<>();
            activeRegions.add(primaryRegion);
            activeRegions.addAll(standbyRegions);
        }

        Map<String, Object> values = baseValues(workspaceId);
        values.put("model_key", request.getModelKey().trim());
        values.put("name", request.getName().trim());
        values.put("topology_mode", topology);
        values.put("status", normalize(defaultValue(request.getStatus(), "ACTIVE")));
        values.put("primary_region", primaryRegion);
        values.put("standby_regions", toJson(standbyRegions));
        values.put("active_regions", toJson(activeRegions));
        values.put("rpo_target_minutes", request.getRpoTargetMinutes() == null ? 15 : request.getRpoTargetMinutes());
        values.put("rto_target_minutes", request.getRtoTargetMinutes() == null ? 60 : request.getRtoTargetMinutes());
        values.put("traffic_policy", toJson(request.getTrafficPolicy()));
        values.put("promotion_state", normalize(defaultValue(request.getPromotionState(), "PRIMARY_HEALTHY")));
        values.put("failover_state", normalize(defaultValue(request.getFailoverState(), "LOCKED")));
        values.put("last_drill_at", null);
        values.put("metadata", toJson(request.getMetadata()));
        return upsertByKey("global_operating_models", "model_key", request.getModelKey().trim(), workspaceId, values,
                List.of("standby_regions", "active_regions", "traffic_policy", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOperatingModels(String workspaceId) {
        return listScoped("global_operating_models", workspaceId, "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createFailoverDrill(GlobalEnterpriseDto.FailoverDrillRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> model = request.getOperatingModelId() == null || request.getOperatingModelId().isBlank()
                ? latestOperatingModel(workspaceId).orElse(Collections.emptyMap())
                : requireById("global_operating_models", request.getOperatingModelId());
        int plannedRpo = request.getPlannedRpoMinutes() == null ? intValue(model.get("rpo_target_minutes"), 15) : request.getPlannedRpoMinutes();
        int plannedRto = request.getPlannedRtoMinutes() == null ? intValue(model.get("rto_target_minutes"), 60) : request.getPlannedRtoMinutes();
        int actualRpo = request.getActualRpoMinutes() == null ? 0 : request.getActualRpoMinutes();
        int actualRto = request.getActualRtoMinutes() == null ? 0 : request.getActualRtoMinutes();
        List<Map<String, Object>> findings = request.getFindings() == null ? List.of() : request.getFindings();
        boolean pass = actualRpo <= plannedRpo && actualRto <= plannedRto && findings.stream().noneMatch(this::criticalFinding);

        Map<String, Object> values = baseValues(workspaceId);
        values.put("operating_model_id", model.get("id"));
        values.put("drill_type", normalize(defaultValue(request.getDrillType(), "PLANNED")));
        values.put("source_region", blankToNull(request.getSourceRegion()));
        values.put("target_region", blankToNull(request.getTargetRegion()));
        values.put("affected_services", toJson(request.getAffectedServices() == null ? List.of() : request.getAffectedServices()));
        values.put("planned_rpo_minutes", plannedRpo);
        values.put("planned_rto_minutes", plannedRto);
        values.put("actual_rpo_minutes", actualRpo);
        values.put("actual_rto_minutes", actualRto);
        values.put("verdict", pass ? "PASS" : "FAIL");
        values.put("findings", toJson(findings));
        values.put("evidence", toJson(request.getEvidence()));
        values.put("started_at", Instant.now());
        values.put("completed_at", request.getCompletedAt() == null ? Instant.now() : request.getCompletedAt());
        Map<String, Object> saved = repository.insert("global_failover_drills", values, List.of("affected_services", "findings", "evidence"));
        if (model.get("id") != null) {
            repository.updateByIdAndWorkspace("global_operating_models", String.valueOf(model.get("id")), tenant(), asString(model.get("workspace_id")),
                    map("last_drill_at", Instant.now(), "failover_state", pass ? "DRILL_PASS" : "DRILL_FAIL"),
                    List.of());
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFailoverDrills(String workspaceId, int limit) {
        Map<String, Object> params = scopedParams(workspaceId);
        params.put("limit", clamp(limit, 1, 500));
        return repository.queryForList("""
                SELECT * FROM global_failover_drills
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> evaluateFailover(GlobalEnterpriseDto.FailoverEvaluationRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> model = findOperatingModel(workspaceId, request.getOperatingModelKey())
                .orElse(Collections.emptyMap());
        String topology = normalize(defaultValue(request.getTopologyMode(), asString(model.get("topology_mode"))));
        if (topology.isBlank()) {
            topology = "ACTIVE_WARM";
        }
        List<String> reasons = new ArrayList<>();
        boolean allowed = true;

        Map<String, Object> residency = findResidencyPolicy(workspaceId, request.getDataClass()).orElse(null);
        if (residency == null) {
            allowed = false;
            reasons.add("No active data residency policy exists for data class; failover blocked.");
        } else {
            String target = normalizeRegion(request.getTargetRegion());
            List<String> allowedRegions = readStringList(residency.get("allowed_regions"));
            List<String> blockedRegions = readStringList(residency.get("blocked_regions"));
            String homeRegion = normalizeRegion(asString(residency.get("home_region")));
            if (!Boolean.TRUE.equals(residency.get("failover_allowed"))) {
                allowed = false;
                reasons.add("Residency policy does not allow regional failover.");
            }
            if (!allowedRegions.isEmpty() && !allowedRegions.contains(target) && !target.equals(homeRegion)) {
                allowed = false;
                reasons.add("Target region is not in allowed residency regions.");
            }
            if (blockedRegions.contains(target)) {
                allowed = false;
                reasons.add("Target region is blocked by residency policy.");
            }
        }

        if (request.getSourceRegion().equalsIgnoreCase(request.getTargetRegion())) {
            allowed = false;
            reasons.add("Source and target regions are the same.");
        }
        if (!TOPOLOGY_MODES.contains(topology)) {
            allowed = false;
            reasons.add("Unsupported topology mode.");
        }
        if ("ACTIVE_WARM".equals(topology) && model.get("id") != null) {
            List<String> standby = readStringList(model.get("standby_regions"));
            if (!standby.contains(normalizeRegion(request.getTargetRegion()))) {
                allowed = false;
                reasons.add("Active-warm failover requires target region to be configured as standby.");
            }
        }
        if ("ACTIVE_ACTIVE".equals(topology) && model.get("id") != null) {
            List<String> activeRegions = readStringList(model.get("active_regions"));
            if (!activeRegions.contains(normalizeRegion(request.getTargetRegion()))) {
                allowed = false;
                reasons.add("Active-active failover requires target region to be active.");
            }
        }
        if (allowed) {
            reasons.add("Failover allowed by topology and residency policy.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allowed", allowed);
        result.put("topologyMode", topology);
        result.put("sourceRegion", normalizeRegion(request.getSourceRegion()));
        result.put("targetRegion", normalizeRegion(request.getTargetRegion()));
        result.put("dataClass", normalize(request.getDataClass()));
        result.put("rpoTargetMinutes", intValue(model.get("rpo_target_minutes"), 15));
        result.put("rtoTargetMinutes", intValue(model.get("rto_target_minutes"), 60));
        result.put("decision", allowed ? "ALLOW" : "BLOCK");
        result.put("reasons", reasons);
        result.put("evaluatedAt", Instant.now().toString());
        return result;
    }

    @Transactional
    public Map<String, Object> upsertDataResidencyPolicy(GlobalEnterpriseDto.DataResidencyPolicyRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> values = baseValues(workspaceId);
        values.put("policy_key", request.getPolicyKey().trim());
        values.put("data_class", normalize(request.getDataClass()));
        values.put("home_region", normalizeRegion(request.getHomeRegion()));
        values.put("allowed_regions", toJson(normalizedList(request.getAllowedRegions())));
        values.put("blocked_regions", toJson(normalizedList(request.getBlockedRegions())));
        values.put("failover_allowed", request.getFailoverAllowed() != null && request.getFailoverAllowed());
        values.put("legal_basis", normalize(defaultValue(request.getLegalBasis(), "CONTRACT")));
        values.put("enforcement_mode", normalize(defaultValue(request.getEnforcementMode(), "ENFORCE")));
        values.put("status", normalize(defaultValue(request.getStatus(), "ACTIVE")));
        values.put("metadata", toJson(request.getMetadata()));
        return upsertByKey("tenant_data_residency_policies", "policy_key", request.getPolicyKey().trim(), workspaceId, values,
                List.of("allowed_regions", "blocked_regions", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDataResidencyPolicies(String workspaceId) {
        return listScoped("tenant_data_residency_policies", workspaceId, "data_class ASC, created_at DESC");
    }

    @Transactional
    public Map<String, Object> upsertEncryptionPolicy(GlobalEnterpriseDto.EncryptionPolicyRequest request) {
        int rotationDays = request.getRotationDays() == null ? 90 : request.getRotationDays();
        if (rotationDays < 1 || rotationDays > 3650) {
            throw new IllegalArgumentException("rotationDays must be between 1 and 3650");
        }
        String workspaceId = workspace(request.getWorkspaceId());
        Instant lastRotatedAt = request.getLastRotatedAt();
        Map<String, Object> values = baseValues(workspaceId);
        values.put("policy_key", request.getPolicyKey().trim());
        values.put("data_class", normalize(request.getDataClass()));
        values.put("key_provider", normalize(request.getKeyProvider()));
        values.put("key_ref", request.getKeyRef().trim());
        values.put("algorithm", defaultValue(request.getAlgorithm(), "AES-256-GCM"));
        values.put("rotation_days", rotationDays);
        values.put("residency_policy_id", blankToNull(request.getResidencyPolicyId()));
        values.put("status", normalize(defaultValue(request.getStatus(), "ACTIVE")));
        values.put("last_rotated_at", lastRotatedAt);
        values.put("next_rotation_at", (lastRotatedAt == null ? Instant.now() : lastRotatedAt).plus(rotationDays, ChronoUnit.DAYS));
        values.put("metadata", toJson(request.getMetadata()));
        return upsertByKey("tenant_encryption_policies", "policy_key", request.getPolicyKey().trim(), workspaceId, values, List.of("metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEncryptionPolicies(String workspaceId) {
        return listScoped("tenant_encryption_policies", workspaceId, "data_class ASC, created_at DESC");
    }

    @Transactional
    public Map<String, Object> createLegalHold(GlobalEnterpriseDto.LegalHoldRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> values = baseValues(workspaceId);
        values.put("hold_key", request.getHoldKey().trim());
        values.put("subject_type", normalize(request.getSubjectType()));
        values.put("subject_id", request.getSubjectId().trim());
        values.put("data_domains", toJson(request.getDataDomains() == null ? List.of() : request.getDataDomains()));
        values.put("reason", request.getReason().trim());
        values.put("status", "ACTIVE");
        values.put("applied_by", actor());
        values.put("applied_at", Instant.now());
        values.put("released_by", null);
        values.put("released_at", null);
        values.put("release_reason", null);
        values.put("evidence", toJson(request.getEvidence()));
        return upsertByKey("governance_legal_holds", "hold_key", request.getHoldKey().trim(), workspaceId, values,
                List.of("data_domains", "evidence"));
    }

    @Transactional
    public Map<String, Object> releaseLegalHold(String id, GlobalEnterpriseDto.LegalHoldReleaseRequest request) {
        Map<String, Object> hold = requireById("governance_legal_holds", id);
        if (!"ACTIVE".equalsIgnoreCase(asString(hold.get("status")))) {
            throw new IllegalArgumentException("Only ACTIVE legal holds can be released");
        }
        return repository.updateByIdAndWorkspace("governance_legal_holds", id, tenant(), asString(hold.get("workspace_id")),
                map("status", "RELEASED",
                        "released_by", actor(),
                        "released_at", Instant.now(),
                        "release_reason", request.getReleaseReason(),
                        "evidence", toJson(request.getEvidence())),
                List.of("evidence"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listLegalHolds(String workspaceId, String status, int limit) {
        Map<String, Object> params = scopedParams(workspaceId);
        params.put("status", blankToNull(status) == null ? null : normalize(status));
        params.put("limit", clamp(limit, 1, 500));
        return repository.queryForList("""
                SELECT * FROM governance_legal_holds
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND (:status IS NULL OR status = :status)
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public Map<String, Object> recordLineageEdge(GlobalEnterpriseDto.LineageEdgeRequest request) {
        Map<String, Object> values = baseValues(workspace(request.getWorkspaceId()));
        values.put("source_type", normalize(request.getSourceType()));
        values.put("source_id", request.getSourceId().trim());
        values.put("target_type", normalize(request.getTargetType()));
        values.put("target_id", request.getTargetId().trim());
        values.put("data_class", normalize(request.getDataClass()));
        values.put("transform_type", normalize(defaultValue(request.getTransformType(), "COPY")));
        values.put("purpose", blankToNull(request.getPurpose()));
        values.put("policy_refs", toJson(request.getPolicyRefs() == null ? List.of() : request.getPolicyRefs()));
        values.put("confidence", request.getConfidence() == null ? 1.0 : request.getConfidence());
        values.put("metadata", toJson(request.getMetadata()));
        return repository.insert("governance_data_lineage_edges", values, List.of("policy_refs", "metadata"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listLineage(String workspaceId, String resourceType, String resourceId, int limit) {
        Map<String, Object> params = scopedParams(workspaceId);
        params.put("resourceType", blankToNull(resourceType) == null ? null : normalize(resourceType));
        params.put("resourceId", blankToNull(resourceId));
        params.put("limit", clamp(limit, 1, 1000));
        return repository.queryForList("""
                SELECT * FROM governance_data_lineage_edges
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND (:resourceType IS NULL OR source_type = :resourceType OR target_type = :resourceType)
                  AND (:resourceId IS NULL OR source_id = :resourceId OR target_id = :resourceId)
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public Map<String, Object> runPolicySimulation(GlobalEnterpriseDto.PolicySimulationRequest request) {
        Map<String, Object> context = safeMap(request.getInputContext());
        List<Map<String, Object>> findings = simulationFindings(context);
        String verdict = findings.stream().anyMatch(this::criticalFinding)
                ? "BLOCK"
                : findings.isEmpty() ? "PASS" : "REVIEW";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verdict", verdict);
        result.put("checks", Map.of(
                "brand", !Boolean.TRUE.equals(context.get("brandViolation")),
                "compliance", !Boolean.TRUE.equals(context.get("complianceViolation")),
                "residency", !Boolean.TRUE.equals(context.get("residencyViolation")),
                "legalHold", !Boolean.TRUE.equals(context.get("legalHoldActive"))
        ));
        result.put("simulatedAt", Instant.now().toString());

        Map<String, Object> values = baseValues(workspace(request.getWorkspaceId()));
        values.put("simulation_key", request.getSimulationKey().trim());
        values.put("policy_type", normalize(request.getPolicyType()));
        values.put("artifact_type", normalize(request.getArtifactType()));
        values.put("artifact_id", blankToNull(request.getArtifactId()));
        values.put("input_context", toJson(context));
        values.put("result", toJson(result));
        values.put("verdict", verdict);
        values.put("findings", toJson(findings));
        values.put("simulated_at", Instant.now());
        return repository.insert("governance_policy_simulation_runs", values, List.of("input_context", "result", "findings"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPolicySimulations(String workspaceId, int limit) {
        Map<String, Object> params = scopedParams(workspaceId);
        params.put("limit", clamp(limit, 1, 500));
        return repository.queryForList("""
                SELECT * FROM governance_policy_simulation_runs
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND deleted_at IS NULL
                ORDER BY simulated_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public Map<String, Object> createEvidencePack(GlobalEnterpriseDto.EvidencePackRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> values = baseValues(workspaceId);
        values.put("pack_key", request.getPackKey().trim());
        values.put("name", request.getName().trim());
        values.put("status", "READY");
        values.put("scope", toJson(request.getScope()));
        values.put("evidence_refs", toJson(request.getEvidenceRefs() == null ? List.of() : request.getEvidenceRefs()));
        values.put("generated_uri", "evidence-pack://" + tenant() + "/" + request.getPackKey().trim() + "/" + values.get("id"));
        values.put("generated_at", Instant.now());
        values.put("expires_at", request.getExpiresAt());
        return upsertByKey("governance_evidence_packs", "pack_key", request.getPackKey().trim(), workspaceId, values,
                List.of("scope", "evidence_refs"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEvidencePacks(String workspaceId, int limit) {
        Map<String, Object> params = scopedParams(workspaceId);
        params.put("limit", clamp(limit, 1, 500));
        return repository.queryForList("""
                SELECT * FROM governance_evidence_packs
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND deleted_at IS NULL
                ORDER BY generated_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public List<Map<String, Object>> seedConnectorTemplates() {
        List<Map<String, Object>> seeded = new ArrayList<>();
        for (ConnectorSeed seed : ConnectorSeed.defaults()) {
            GlobalEnterpriseDto.ConnectorTemplateRequest request = new GlobalEnterpriseDto.ConnectorTemplateRequest();
            request.setConnectorKey(seed.key());
            request.setCategory(seed.category());
            request.setDisplayName(seed.name());
            request.setVendor(seed.vendor());
            request.setAuthModes(seed.authModes());
            request.setSupportedEvents(seed.events());
            request.setCapabilities(seed.capabilities());
            request.setStatus("ACTIVE");
            request.setMetadata(Map.of("seeded", true));
            seeded.add(upsertConnectorTemplate(request));
        }
        return seeded;
    }

    @Transactional
    public Map<String, Object> upsertConnectorTemplate(GlobalEnterpriseDto.ConnectorTemplateRequest request) {
        Map<String, Object> values = baseValues(null);
        values.put("workspace_id", null);
        values.put("connector_key", request.getConnectorKey().trim());
        values.put("category", normalize(request.getCategory()));
        values.put("display_name", request.getDisplayName().trim());
        values.put("vendor", request.getVendor().trim());
        values.put("auth_modes", toJson(normalizedEnumList(request.getAuthModes())));
        values.put("supported_events", toJson(request.getSupportedEvents() == null ? List.of() : request.getSupportedEvents()));
        values.put("capabilities", toJson(request.getCapabilities()));
        values.put("status", normalize(defaultValue(request.getStatus(), "ACTIVE")));
        values.put("metadata", toJson(request.getMetadata()));
        return upsertByKeyNoWorkspace("marketplace_connector_templates", "connector_key", request.getConnectorKey().trim(), values,
                List.of("auth_modes", "supported_events", "capabilities", "metadata"));
    }

    @Transactional
    public List<Map<String, Object>> listConnectorTemplates(String category) {
        List<Map<String, Object>> existing = repository.queryForList("""
                SELECT * FROM marketplace_connector_templates
                WHERE tenant_id = :tenantId
                  AND (:category IS NULL OR category = :category)
                  AND deleted_at IS NULL
                ORDER BY category, display_name
                """, map("tenantId", tenant(), "category", blankToNull(category) == null ? null : normalize(category)));
        if (existing.isEmpty() && blankToNull(category) == null) {
            return seedConnectorTemplates();
        }
        return existing;
    }

    @Transactional
    public Map<String, Object> upsertConnectorInstance(GlobalEnterpriseDto.ConnectorInstanceRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> template = findConnectorTemplate(request.getTemplateId(), request.getConnectorKey()).orElse(Collections.emptyMap());
        List<String> authModes = readStringList(template.get("auth_modes"));
        String authMode = normalize(request.getAuthMode());
        if (!authModes.isEmpty() && !authModes.contains(authMode)) {
            throw new IllegalArgumentException("Unsupported connector auth mode: " + authMode);
        }
        boolean credentialNeeded = !"NONE".equals(authMode) && !"WEBHOOK_SIGNED".equals(authMode);
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("valid", !credentialNeeded || blankToNull(request.getCredentialRef()) != null);
        validation.put("dryRunOnly", true);
        validation.put("message", validation.get("valid").equals(true)
                ? "Connector config validates for dry-run sync."
                : "Credential reference required before live sync.");

        Map<String, Object> values = baseValues(workspaceId);
        values.put("template_id", template.get("id"));
        values.put("instance_key", request.getInstanceKey().trim());
        values.put("connector_key", request.getConnectorKey().trim());
        values.put("display_name", request.getDisplayName().trim());
        values.put("category", normalize(request.getCategory()));
        values.put("status", normalize(defaultValue(request.getStatus(), "DRAFT")));
        values.put("auth_mode", authMode);
        values.put("credential_ref", blankToNull(request.getCredentialRef()));
        values.put("config", toJson(request.getConfig()));
        values.put("last_validated_at", Instant.now());
        values.put("validation_result", toJson(validation));
        return upsertByKey("marketplace_connector_instances", "instance_key", request.getInstanceKey().trim(), workspaceId, values,
                List.of("config", "validation_result"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listConnectorInstances(String workspaceId) {
        return listScoped("marketplace_connector_instances", workspaceId, "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createSyncJob(GlobalEnterpriseDto.SyncJobRequest request) {
        Map<String, Object> instance = requireById("marketplace_connector_instances", request.getConnectorInstanceId());
        boolean dryRun = request.getDryRun() == null || request.getDryRun();
        if (!dryRun && blankToNull(asString(instance.get("credential_ref"))) == null) {
            throw new IllegalArgumentException("Live connector sync requires credentialRef");
        }
        Map<String, Object> values = baseValues(workspace(request.getWorkspaceId()));
        values.put("connector_instance_id", request.getConnectorInstanceId());
        values.put("sync_type", normalize(request.getSyncType()));
        values.put("direction", normalize(defaultValue(request.getDirection(), "IMPORT")));
        values.put("dry_run", dryRun);
        values.put("status", dryRun ? "DRY_RUN_COMPLETE" : "QUEUED");
        values.put("request", toJson(request.getRequest()));
        values.put("result", toJson(syncResult(instance, request, dryRun)));
        values.put("estimated_records", Math.round(number(safeMap(request.getRequest()).get("limit"), 100)));
        values.put("started_at", Instant.now());
        values.put("completed_at", dryRun ? Instant.now() : null);
        return repository.insert("marketplace_sync_jobs", values, List.of("request", "result"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSyncJobs(String workspaceId, int limit) {
        Map<String, Object> params = scopedParams(workspaceId);
        params.put("limit", clamp(limit, 1, 500));
        return repository.queryForList("""
                SELECT * FROM marketplace_sync_jobs
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public Map<String, Object> upsertOptimizationPolicy(GlobalEnterpriseDto.OptimizationPolicyRequest request) {
        String mode = normalize(defaultValue(request.getMode(), "SUGGEST_ONLY"));
        if (!OPTIMIZATION_MODES.contains(mode)) {
            throw new IllegalArgumentException("mode must be SUGGEST_ONLY or AUTO_APPLY_WITH_GUARDRAILS");
        }
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> values = baseValues(workspaceId);
        values.put("policy_key", request.getPolicyKey().trim());
        values.put("name", request.getName().trim());
        values.put("mode", mode);
        values.put("status", normalize(defaultValue(request.getStatus(), "ACTIVE")));
        values.put("target_scope", toJson(request.getTargetScope()));
        values.put("constraints", toJson(request.getConstraints()));
        values.put("guardrails", toJson(request.getGuardrails()));
        values.put("rollback_policy", toJson(request.getRollbackPolicy()));
        return upsertByKey("autonomous_optimization_policies", "policy_key", request.getPolicyKey().trim(), workspaceId, values,
                List.of("target_scope", "constraints", "guardrails", "rollback_policy"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOptimizationPolicies(String workspaceId) {
        return listScoped("autonomous_optimization_policies", workspaceId, "created_at DESC");
    }

    @Transactional
    public Map<String, Object> createOptimizationRecommendation(GlobalEnterpriseDto.OptimizationRecommendationRequest request) {
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> policy = findOptimizationPolicy(workspaceId, request.getPolicyKey())
                .orElseThrow(() -> new IllegalArgumentException("Active optimization policy not found: " + request.getPolicyKey()));
        Map<String, Object> signals = safeMap(request.getInputSignals());
        Map<String, Object> recommendation = safeMap(request.getRecommendation());
        int riskScore = optimizationRiskScore(policy, signals, recommendation);
        boolean brandPass = guardrailPass(signals, recommendation, "brand");
        boolean compliancePass = guardrailPass(signals, recommendation, "compliance");
        boolean simulationPass = Boolean.TRUE.equals(signals.getOrDefault("policySimulationPassed", false));
        boolean autoApply = "AUTO_APPLY_WITH_GUARDRAILS".equals(policy.get("mode"))
                && riskScore < 40
                && brandPass
                && compliancePass
                && simulationPass;

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("riskScore", riskScore);
        explanation.put("signalsUsed", signals.keySet());
        explanation.put("brandPass", brandPass);
        explanation.put("compliancePass", compliancePass);
        explanation.put("policySimulationPassed", simulationPass);
        explanation.put("decisionRule", autoApply ? "Auto-applied because all guardrails passed." : "Human approval required before apply.");

        Map<String, Object> values = baseValues(workspaceId);
        values.put("policy_id", policy.get("id"));
        values.put("artifact_type", normalize(request.getArtifactType()));
        values.put("artifact_id", request.getArtifactId().trim());
        values.put("status", autoApply ? "APPLIED" : "PENDING_APPROVAL");
        values.put("risk_score", riskScore);
        values.put("input_signals", toJson(signals));
        values.put("explanation", toJson(explanation));
        values.put("recommendation", toJson(recommendation));
        values.put("brand_result", toJson(Map.of("passed", brandPass)));
        values.put("compliance_result", toJson(Map.of("passed", compliancePass, "simulationPassed", simulationPass)));
        values.put("target_snapshot", toJson(request.getTargetSnapshot()));
        values.put("applied_snapshot", toJson(autoApply ? recommendation : Collections.emptyMap()));
        values.put("rollback_snapshot", toJson(request.getTargetSnapshot()));
        values.put("decision_by", autoApply ? "SYSTEM" : null);
        values.put("decision_at", autoApply ? Instant.now() : null);
        values.put("decision_note", autoApply ? "AUTO_APPLY_WITH_GUARDRAILS" : null);
        values.put("applied_at", autoApply ? Instant.now() : null);
        return repository.insert("autonomous_optimization_recommendations", values,
                List.of("input_signals", "explanation", "recommendation", "brand_result", "compliance_result", "target_snapshot", "applied_snapshot", "rollback_snapshot"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOptimizationRecommendations(String workspaceId, String status, int limit) {
        Map<String, Object> params = scopedParams(workspaceId);
        params.put("status", blankToNull(status) == null ? null : normalize(status));
        params.put("limit", clamp(limit, 1, 500));
        return repository.queryForList("""
                SELECT * FROM autonomous_optimization_recommendations
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND (:status IS NULL OR status = :status)
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """, params);
    }

    @Transactional
    public Map<String, Object> decideOptimizationRecommendation(String id, GlobalEnterpriseDto.OptimizationDecisionRequest request) {
        Map<String, Object> recommendation = requireById("autonomous_optimization_recommendations", id);
        String decision = normalize(request.getDecision());
        if (!List.of("APPROVED", "REJECTED", "APPLY").contains(decision)) {
            throw new IllegalArgumentException("decision must be APPROVED, REJECTED, or APPLY");
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("status", "APPLY".equals(decision) ? "APPLIED" : decision);
        updates.put("decision_by", actor());
        updates.put("decision_at", Instant.now());
        updates.put("decision_note", blankToNull(request.getDecisionNote()));
        if ("APPLY".equals(decision)) {
            updates.put("applied_snapshot", toJson(request.getAppliedSnapshot()));
            updates.put("applied_at", Instant.now());
        }
        return repository.updateByIdAndWorkspace("autonomous_optimization_recommendations", id, tenant(), asString(recommendation.get("workspace_id")), updates, List.of("applied_snapshot"));
    }

    @Transactional
    public Map<String, Object> createOptimizationRollback(GlobalEnterpriseDto.OptimizationRollbackRequest request) {
        Map<String, Object> recommendation = requireById("autonomous_optimization_recommendations", request.getRecommendationId());
        Map<String, Object> values = baseValues(asString(recommendation.get("workspace_id")));
        values.put("recommendation_id", request.getRecommendationId());
        values.put("status", "COMPLETED");
        values.put("reason", request.getReason().trim());
        values.put("rollback_snapshot", toJson(readMap(recommendation.get("rollback_snapshot"))));
        values.put("evidence", toJson(request.getEvidence()));
        values.put("rolled_back_at", Instant.now());
        Map<String, Object> rollback = repository.insert("autonomous_optimization_rollbacks", values, List.of("rollback_snapshot", "evidence"));
        repository.updateByIdAndWorkspace("autonomous_optimization_recommendations", request.getRecommendationId(), tenant(), asString(recommendation.get("workspace_id")),
                map("status", "ROLLED_BACK"), List.of());
        return rollback;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOptimizationRollbacks(String workspaceId, int limit) {
        Map<String, Object> params = scopedParams(workspaceId);
        params.put("limit", clamp(limit, 1, 500));
        return repository.queryForList("""
                SELECT * FROM autonomous_optimization_rollbacks
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND deleted_at IS NULL
                ORDER BY created_at DESC
                LIMIT :limit
                """, params);
    }

    private Map<String, Object> upsertByKey(String table, String keyColumn, String keyValue, String workspaceId,
                                            Map<String, Object> values, List<String> jsonColumns) {
        String safeTable = CorePlatformRepository.safeTable(table);
        String safeKeyColumn = CorePlatformRepository.safeKeyColumn(keyColumn);
        List<Map<String, Object>> existing = repository.queryForList(
                "SELECT id FROM " + safeTable + " WHERE tenant_id = :tenantId AND COALESCE(workspace_id, '') = COALESCE(:workspaceId, '') AND " + safeKeyColumn + " = :keyValue AND deleted_at IS NULL LIMIT 1",
                map("tenantId", tenant(), "workspaceId", workspaceId, "keyValue", keyValue)
        );
        if (existing.isEmpty()) {
            return repository.insert(table, values, jsonColumns);
        }
        Map<String, Object> updates = new LinkedHashMap<>(values);
        updates.keySet().removeAll(List.of("id", "tenant_id", "workspace_id", "created_at", "created_by", "deleted_at", "version"));
        if (workspaceId != null) {
            return repository.updateByIdAndWorkspace(table, String.valueOf(existing.get(0).get("id")), tenant(), workspaceId, updates, jsonColumns);
        }
        return repository.updateById(table, String.valueOf(existing.get(0).get("id")), tenant(), updates, jsonColumns);
    }

    private Map<String, Object> upsertByKeyNoWorkspace(String table, String keyColumn, String keyValue,
                                                       Map<String, Object> values, List<String> jsonColumns) {
        String safeTable = CorePlatformRepository.safeTable(table);
        String safeKeyColumn = CorePlatformRepository.safeKeyColumn(keyColumn);
        List<Map<String, Object>> existing = repository.queryForList(
                "SELECT id FROM " + safeTable + " WHERE tenant_id = :tenantId AND " + safeKeyColumn + " = :keyValue AND deleted_at IS NULL LIMIT 1",
                map("tenantId", tenant(), "keyValue", keyValue)
        );
        if (existing.isEmpty()) {
            return repository.insert(table, values, jsonColumns);
        }
        Map<String, Object> updates = new LinkedHashMap<>(values);
        updates.keySet().removeAll(List.of("id", "tenant_id", "workspace_id", "created_at", "created_by", "deleted_at", "version"));
        return repository.updateById(table, String.valueOf(existing.get(0).get("id")), tenant(), updates, jsonColumns);
    }

    private List<Map<String, Object>> listScoped(String table, String workspaceId, String orderBy) {
        Map<String, Object> params = scopedParams(workspaceId);
        return repository.queryForList(
                "SELECT * FROM " + CorePlatformRepository.safeTable(table) + " WHERE tenant_id = :tenantId AND (:workspaceId IS NULL OR workspace_id = :workspaceId) AND deleted_at IS NULL ORDER BY " + CorePlatformRepository.safeOrderBy(orderBy),
                params
        );
    }

    private Map<String, Object> requireById(String table, String id) {
        String safeTable = CorePlatformRepository.safeTable(table);
        String workspaceId = workspace(null);
        List<Map<String, Object>> rows = repository.queryForList(
                "SELECT * FROM " + safeTable + " WHERE tenant_id = :tenantId AND workspace_id = :workspaceId AND id = :id AND deleted_at IS NULL LIMIT 1",
                map("tenantId", tenant(), "workspaceId", workspaceId, "id", id)
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException(safeTable + " not found: " + id);
        }
        return rows.get(0);
    }

    private java.util.Optional<Map<String, Object>> latestOperatingModel(String workspaceId) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM global_operating_models
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                ORDER BY updated_at DESC
                LIMIT 1
                """, scopedParams(workspaceId));
        return rows.stream().findFirst();
    }

    private java.util.Optional<Map<String, Object>> findOperatingModel(String workspaceId, String modelKey) {
        if (blankToNull(modelKey) == null) {
            return latestOperatingModel(workspaceId);
        }
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM global_operating_models
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND model_key = :modelKey
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                LIMIT 1
                """, map("tenantId", tenant(), "workspaceId", workspaceId, "modelKey", modelKey));
        return rows.stream().findFirst();
    }

    private java.util.Optional<Map<String, Object>> findResidencyPolicy(String workspaceId, String dataClass) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM tenant_data_residency_policies
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND data_class = :dataClass
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                ORDER BY updated_at DESC
                LIMIT 1
                """, map("tenantId", tenant(), "workspaceId", workspaceId, "dataClass", normalize(dataClass)));
        return rows.stream().findFirst();
    }

    private java.util.Optional<Map<String, Object>> findConnectorTemplate(String templateId, String connectorKey) {
        List<Map<String, Object>> rows;
        if (blankToNull(templateId) != null) {
            rows = repository.queryForList("""
                    SELECT * FROM marketplace_connector_templates
                    WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                    LIMIT 1
                    """, map("tenantId", tenant(), "id", templateId));
        } else {
            rows = repository.queryForList("""
                    SELECT * FROM marketplace_connector_templates
                    WHERE tenant_id = :tenantId AND connector_key = :connectorKey AND deleted_at IS NULL
                    LIMIT 1
                    """, map("tenantId", tenant(), "connectorKey", connectorKey));
        }
        return rows.stream().findFirst();
    }

    private java.util.Optional<Map<String, Object>> findOptimizationPolicy(String workspaceId, String policyKey) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT * FROM autonomous_optimization_policies
                WHERE tenant_id = :tenantId
                  AND (:workspaceId IS NULL OR workspace_id = :workspaceId)
                  AND policy_key = :policyKey
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                LIMIT 1
                """, map("tenantId", tenant(), "workspaceId", workspaceId, "policyKey", policyKey));
        return rows.stream().findFirst();
    }

    private List<Map<String, Object>> simulationFindings(Map<String, Object> context) {
        List<Map<String, Object>> findings = new ArrayList<>();
        if (Boolean.TRUE.equals(context.get("legalHoldActive"))) {
            findings.add(finding("LEGAL_HOLD", "BLOCK", "Active legal hold blocks destructive or regional movement."));
        }
        if (Boolean.TRUE.equals(context.get("residencyViolation"))) {
            findings.add(finding("DATA_RESIDENCY", "BLOCK", "Target action violates data residency policy."));
        }
        if (Boolean.TRUE.equals(context.get("complianceViolation"))) {
            findings.add(finding("COMPLIANCE", "BLOCK", "Compliance constraint failed."));
        }
        if (Boolean.TRUE.equals(context.get("brandViolation"))) {
            findings.add(finding("BRAND", "REVIEW", "Brand constraint requires human review."));
        }
        return findings;
    }

    private Map<String, Object> syncResult(Map<String, Object> instance, GlobalEnterpriseDto.SyncJobRequest request, boolean dryRun) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("connectorKey", instance.get("connector_key"));
        result.put("syncType", normalize(request.getSyncType()));
        result.put("dryRun", dryRun);
        result.put("estimatedRecords", Math.round(number(safeMap(request.getRequest()).get("limit"), 100)));
        result.put("message", dryRun ? "Dry-run completed; no external connector call made." : "Live sync queued for connector worker.");
        return result;
    }

    private int optimizationRiskScore(Map<String, Object> policy, Map<String, Object> signals, Map<String, Object> recommendation) {
        int risk = 0;
        risk += (int) number(signals.get("riskScore"), 0);
        if (Boolean.TRUE.equals(signals.get("usesSensitiveData"))) {
            risk += 25;
        }
        if (Boolean.TRUE.equals(recommendation.get("changesBrandCopy"))) {
            risk += 15;
        }
        if (Boolean.TRUE.equals(recommendation.get("changesAudience"))) {
            risk += 20;
        }
        if (!readMap(policy.get("constraints")).isEmpty()) {
            risk += 5;
        }
        return clamp(risk, 0, 100);
    }

    private boolean guardrailPass(Map<String, Object> signals, Map<String, Object> recommendation, String area) {
        String key = area + "Compliant";
        Object explicit = recommendation.get(key);
        if (explicit == null) {
            explicit = signals.get(key);
        }
        if (explicit != null) {
            return Boolean.TRUE.equals(explicit) || "true".equalsIgnoreCase(String.valueOf(explicit));
        }
        return !Boolean.TRUE.equals(signals.get(area + "Violation")) && !Boolean.TRUE.equals(recommendation.get(area + "Violation"));
    }

    private Map<String, Object> finding(String key, String severity, String message) {
        Map<String, Object> finding = new LinkedHashMap<>();
        finding.put("key", key);
        finding.put("severity", severity);
        finding.put("message", message);
        return finding;
    }

    private boolean criticalFinding(Map<String, Object> finding) {
        String severity = normalize(asString(finding.get("severity")));
        return "CRITICAL".equals(severity) || "BLOCK".equals(severity) || "P0".equals(severity);
    }

    private Map<String, Object> baseValues(String workspaceId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", IdGenerator.newId());
        values.put("tenant_id", tenant());
        values.put("workspace_id", workspaceId);
        values.put("created_at", Instant.now());
        values.put("updated_at", Instant.now());
        values.put("created_by", actor());
        values.put("deleted_at", null);
        values.put("version", 0L);
        return values;
    }

    private Map<String, Object> scopedParams(String workspaceId) {
        return map("tenantId", tenant(), "workspaceId", workspace(workspaceId));
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return values;
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Collections.emptyMap() : value;
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) {
            return Collections.emptyMap();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, val) -> result.put(String.valueOf(key), val));
            return result;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), MAP_TYPE);
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), STRING_LIST)
                    .stream()
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> normalizedList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalizeRegion)
                .distinct()
                .toList();
    }

    private List<String> normalizedEnumList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalize)
                .distinct()
                .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize global enterprise payload", e);
        }
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double number(Object value, double fallback) {
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String tenant() {
        return TenantContext.requireTenantId();
    }

    private String workspace(String workspaceId) {
        String resolved = blankToNull(workspaceId);
        String contextWorkspaceId = blankToNull(TenantContext.getWorkspaceId());
        if (resolved != null && contextWorkspaceId != null && !contextWorkspaceId.equals(resolved)) {
            throw new IllegalArgumentException("workspaceId does not match the current workspace");
        }
        return resolved == null ? contextWorkspaceId : resolved;
    }

    private String actor() {
        String actor = TenantContext.getUserId();
        return actor == null || actor.isBlank() ? "SYSTEM" : actor;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRegion(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record ConnectorSeed(String key,
                                 String category,
                                 String name,
                                 String vendor,
                                 List<String> authModes,
                                 List<String> events,
                                 Map<String, Object> capabilities) {
        static List<ConnectorSeed> defaults() {
            return List.of(
                    new ConnectorSeed("salesforce-crm", "CRM", "Salesforce CRM", "Salesforce", List.of("OAUTH2", "PRIVATE_APP"), List.of("contact.updated", "lead.created"), Map.of("objects", List.of("Contact", "Lead", "Campaign"))),
                    new ConnectorSeed("segment-cdp", "CDP", "Segment CDP", "Twilio Segment", List.of("API_KEY", "OAUTH2"), List.of("profile.updated", "audience.synced"), Map.of("streams", List.of("profiles", "audiences"))),
                    new ConnectorSeed("shopify-commerce", "ECOMMERCE", "Shopify", "Shopify", List.of("OAUTH2", "PRIVATE_APP"), List.of("order.created", "customer.updated"), Map.of("objects", List.of("Customer", "Order", "Product"))),
                    new ConnectorSeed("snowflake-lakehouse", "LAKEHOUSE", "Snowflake", "Snowflake", List.of("KEYPAIR", "OAUTH2"), List.of("table.extract", "query.result"), Map.of("exports", List.of("campaign_performance", "audience_membership"))),
                    new ConnectorSeed("looker-bi", "BI", "Looker", "Google", List.of("OAUTH2", "API_KEY"), List.of("dashboard.export"), Map.of("reports", List.of("campaign", "deliverability", "revenue"))),
                    new ConnectorSeed("zendesk-support", "SUPPORT", "Zendesk", "Zendesk", List.of("OAUTH2", "API_KEY"), List.of("ticket.created", "user.updated"), Map.of("objects", List.of("Ticket", "User", "Organization")))
            );
        }
    }
}
