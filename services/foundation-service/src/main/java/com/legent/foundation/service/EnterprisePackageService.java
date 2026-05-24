package com.legent.foundation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.legent.foundation.dto.GlobalEnterpriseDto;
import com.legent.foundation.repository.CorePlatformRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EnterprisePackageService {

    public static final String SCHEMA_VERSION = "legent.enterprise-package.v1";

    private static final List<String> SUPPORTED_OBJECT_TYPES = List.of(
            "ADMIN_SETTING",
            "FEATURE_CONTROL",
            "GLOBAL_OPERATING_MODEL",
            "DATA_RESIDENCY_POLICY",
            "ENCRYPTION_POLICY",
            "EVIDENCE_PACK"
    );
    private static final Set<String> SUPPORTED_OBJECT_TYPE_SET = Set.copyOf(SUPPORTED_OBJECT_TYPES);
    private static final Set<String> DENIED_OBJECT_TYPES = Set.of(
            "SUBSCRIBER",
            "CONTACT",
            "DATA_EXTENSION_ROW",
            "CAMPAIGN_RECIPIENT",
            "TRACKING_EVENT",
            "RAW_AUDIT_PAYLOAD",
            "PROVIDER_RESPONSE",
            "CREDENTIAL",
            "PRIVATE_KEY",
            "OBJECT_STORE_KEY"
    );
    private static final Set<String> BLOCKING_SEVERITIES = Set.of("BLOCK", "BLOCKER", "CRITICAL");

    private final CorePlatformRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, Object> exportPackage(GlobalEnterpriseDto.EnterprisePackageExportRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = workspace(request.getWorkspaceId());
        Map<String, Object> sourceEnvironment = requireEnvironment(request.getSourceEnvironmentId(), tenantId, workspaceId);
        List<String> objectTypes = normalizeObjectTypes(request.getObjectTypes(), true);
        List<Map<String, Object>> objects = new ArrayList<>();
        for (String objectType : objectTypes) {
            objects.addAll(loadObjects(objectType, tenantId, workspaceId, request.getSourceEnvironmentId().trim()));
        }
        objects.sort(Comparator.comparing(item -> text(item.get("type")) + ":" + text(item.get("key"))));

        List<Map<String, Object>> findings = scanUnsafe(objects);
        if (!findings.isEmpty()) {
            throw new IllegalArgumentException("Enterprise package export blocked by unsafe package content: "
                    + findings.stream().map(item -> text(item.get("key"))).distinct().limit(5).toList());
        }

        Map<String, Object> manifest = map(
                "schemaVersion", SCHEMA_VERSION,
                "packageKey", request.getPackageKey().trim(),
                "name", request.getName().trim(),
                "source", map(
                        "tenantId", tenantId,
                        "workspaceId", workspaceId,
                        "environmentId", request.getSourceEnvironmentId().trim(),
                        "environmentKey", sourceEnvironment.get("environment_key")
                ),
                "objectTypes", objectTypes,
                "objects", objects,
                "dependencies", dependencies(objects),
                "validation", map(
                        "requiredGates", request.getRequiredValidationGates() == null ? List.of("foundation-package-validate") : request.getRequiredValidationGates(),
                        "supportedObjectTypes", SUPPORTED_OBJECT_TYPES,
                        "deniedObjectTypes", DENIED_OBJECT_TYPES.stream().sorted().toList(),
                        "liveApplySupported", false,
                        "redactionPolicy", "metadata-and-configuration-only"
                ),
                "metadata", safeMap(request.getMetadata()),
                "createdAt", Instant.now().toString(),
                "createdBy", actor()
        );
        String checksum = checksum(manifest);
        manifest.put("checksum", checksum);
        return map(
                "status", "READY",
                "dryRunOnly", true,
                "objectCount", objects.size(),
                "checksum", checksum,
                "manifest", manifest,
                "findings", List.of()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validateImport(GlobalEnterpriseDto.EnterprisePackageImportValidateRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = workspace(request.getWorkspaceId());
        List<Map<String, Object>> findings = new ArrayList<>();
        List<Map<String, Object>> diff = new ArrayList<>();
        Map<String, Object> targetEnvironment = findEnvironment(request.getTargetEnvironmentId(), tenantId, workspaceId);
        if (targetEnvironment == null) {
            findings.add(finding("target_environment.missing", "BLOCK", "Target environment is not in the current workspace."));
        } else if (bool(targetEnvironment.get("active_lock")) || bool(targetEnvironment.get("is_locked"))) {
            findings.add(finding("target_environment.locked", "BLOCK", "Target environment has an active lock; package import can only be validated."));
        }

        Map<String, Object> manifest = safeMap(request.getManifest());
        if (manifest.isEmpty()) {
            findings.add(finding("manifest.missing", "BLOCK", "Package manifest is required."));
        } else {
            validateManifestEnvelope(manifest, tenantId, workspaceId, findings);
            List<Map<String, Object>> objects = readObjects(manifest.get("objects"), findings);
            findings.addAll(scanUnsafe(objects));
            for (Map<String, Object> object : objects) {
                validateObject(object, findings);
            }
            if (!hasBlockingFinding(findings) && targetEnvironment != null) {
                for (Map<String, Object> object : objects) {
                    diff.add(diffObject(object, tenantId, workspaceId, request.getTargetEnvironmentId().trim()));
                }
            }
        }

        if (Boolean.TRUE.equals(request.getConfirmLiveApply())) {
            findings.add(finding("live_apply.unsupported", "BLOCK", "Live import apply is not implemented in this local contract."));
            if (blankToNull(request.getIdempotencyKey()) == null) {
                findings.add(finding("idempotency_key.missing", "BLOCK", "Live import requests must include an idempotency key."));
            }
        }

        Map<String, Long> summary = diff.stream()
                .collect(LinkedHashMap::new,
                        (acc, item) -> acc.merge(text(item.get("action")), 1L, Long::sum),
                        Map::putAll);
        return map(
                "status", hasBlockingFinding(findings) ? "BLOCKED" : "VALIDATED",
                "dryRunOnly", true,
                "liveApplySupported", false,
                "targetEnvironmentId", request.getTargetEnvironmentId(),
                "diff", diff,
                "diffSummary", summary,
                "findings", findings
        );
    }

    private List<Map<String, Object>> loadObjects(String objectType, String tenantId, String workspaceId, String environmentId) {
        return switch (objectType) {
            case "ADMIN_SETTING" -> loadAdminSettings(tenantId, workspaceId, environmentId);
            case "FEATURE_CONTROL" -> loadFeatureControls(tenantId, workspaceId);
            case "GLOBAL_OPERATING_MODEL" -> loadGlobalOperatingModels(tenantId, workspaceId);
            case "DATA_RESIDENCY_POLICY" -> loadDataResidencyPolicies(tenantId, workspaceId);
            case "ENCRYPTION_POLICY" -> loadEncryptionPolicies(tenantId, workspaceId);
            case "EVIDENCE_PACK" -> loadEvidencePacks(tenantId, workspaceId);
            default -> throw new IllegalArgumentException("Unsupported enterprise package object type: " + objectType);
        };
    }

    private List<Map<String, Object>> loadAdminSettings(String tenantId, String workspaceId, String environmentId) {
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT config_key, config_value, module_key, category, value_type, scope_type, environment_id,
                       is_encrypted, is_system, config_version, dependency_keys, validation_schema, metadata, description
                FROM system_configs
                WHERE tenant_id = :tenantId
                  AND workspace_id = :workspaceId
                  AND deleted_at IS NULL
                  AND scope_type IN ('WORKSPACE', 'ENVIRONMENT')
                  AND (environment_id IS NULL OR environment_id = :environmentId)
                ORDER BY module_key, category, config_key
                """, map("tenantId", tenantId, "workspaceId", workspaceId, "environmentId", environmentId));
        List<Map<String, Object>> objects = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (bool(row.get("is_encrypted"))) {
                throw new IllegalArgumentException("Encrypted admin setting cannot be exported in an enterprise package: " + row.get("config_key"));
            }
            Map<String, Object> payload = map(
                    "key", row.get("config_key"),
                    "value", row.get("config_value"),
                    "module", row.get("module_key"),
                    "category", row.get("category"),
                    "type", row.get("value_type"),
                    "scope", row.get("scope_type"),
                    "sourceEnvironmentId", row.get("environment_id"),
                    "dependencyKeys", row.get("dependency_keys"),
                    "validationSchema", row.get("validation_schema"),
                    "metadata", row.get("metadata"),
                    "description", row.get("description")
            );
            objects.add(packageObject("ADMIN_SETTING", text(row.get("config_key")), row.get("config_version"), payload));
        }
        return objects;
    }

    private List<Map<String, Object>> loadFeatureControls(String tenantId, String workspaceId) {
        return loadWorkspaceRows("""
                SELECT feature_key, enabled, source, dependency_keys, metadata, version
                FROM feature_controls
                WHERE tenant_id = :tenantId AND workspace_id = :workspaceId AND deleted_at IS NULL
                ORDER BY feature_key
                """, tenantId, workspaceId, "FEATURE_CONTROL", "feature_key", row -> map(
                "featureKey", row.get("feature_key"),
                "enabled", row.get("enabled"),
                "source", row.get("source"),
                "dependencyKeys", row.get("dependency_keys"),
                "metadata", row.get("metadata")
        ));
    }

    private List<Map<String, Object>> loadGlobalOperatingModels(String tenantId, String workspaceId) {
        return loadWorkspaceRows("""
                SELECT model_key, name, topology_mode, status, primary_region, standby_regions, active_regions,
                       rpo_target_minutes, rto_target_minutes, traffic_policy, promotion_state, failover_state, metadata, version
                FROM global_operating_models
                WHERE tenant_id = :tenantId AND workspace_id = :workspaceId AND deleted_at IS NULL
                ORDER BY model_key
                """, tenantId, workspaceId, "GLOBAL_OPERATING_MODEL", "model_key", row -> map(
                "modelKey", row.get("model_key"),
                "name", row.get("name"),
                "topologyMode", row.get("topology_mode"),
                "status", row.get("status"),
                "primaryRegion", row.get("primary_region"),
                "standbyRegions", row.get("standby_regions"),
                "activeRegions", row.get("active_regions"),
                "rpoTargetMinutes", row.get("rpo_target_minutes"),
                "rtoTargetMinutes", row.get("rto_target_minutes"),
                "trafficPolicy", row.get("traffic_policy"),
                "promotionState", row.get("promotion_state"),
                "failoverState", row.get("failover_state"),
                "metadata", row.get("metadata")
        ));
    }

    private List<Map<String, Object>> loadDataResidencyPolicies(String tenantId, String workspaceId) {
        return loadWorkspaceRows("""
                SELECT policy_key, data_class, home_region, allowed_regions, blocked_regions, failover_allowed,
                       legal_basis, enforcement_mode, status, metadata, version
                FROM tenant_data_residency_policies
                WHERE tenant_id = :tenantId AND workspace_id = :workspaceId AND deleted_at IS NULL
                ORDER BY policy_key
                """, tenantId, workspaceId, "DATA_RESIDENCY_POLICY", "policy_key", row -> map(
                "policyKey", row.get("policy_key"),
                "dataClass", row.get("data_class"),
                "homeRegion", row.get("home_region"),
                "allowedRegions", row.get("allowed_regions"),
                "blockedRegions", row.get("blocked_regions"),
                "failoverAllowed", row.get("failover_allowed"),
                "legalBasis", row.get("legal_basis"),
                "enforcementMode", row.get("enforcement_mode"),
                "status", row.get("status"),
                "metadata", row.get("metadata")
        ));
    }

    private List<Map<String, Object>> loadEncryptionPolicies(String tenantId, String workspaceId) {
        return loadWorkspaceRows("""
                SELECT policy_key, data_class, key_provider, key_ref, algorithm, rotation_days, status, metadata, version
                FROM tenant_encryption_policies
                WHERE tenant_id = :tenantId AND workspace_id = :workspaceId AND deleted_at IS NULL
                ORDER BY policy_key
                """, tenantId, workspaceId, "ENCRYPTION_POLICY", "policy_key", row -> map(
                "policyKey", row.get("policy_key"),
                "dataClass", row.get("data_class"),
                "keyProvider", row.get("key_provider"),
                "keyRef", row.get("key_ref"),
                "algorithm", row.get("algorithm"),
                "rotationDays", row.get("rotation_days"),
                "status", row.get("status"),
                "metadata", row.get("metadata")
        ));
    }

    private List<Map<String, Object>> loadEvidencePacks(String tenantId, String workspaceId) {
        return loadWorkspaceRows("""
                SELECT pack_key, name, status, scope, evidence_refs, expires_at, version
                FROM governance_evidence_packs
                WHERE tenant_id = :tenantId AND workspace_id = :workspaceId AND deleted_at IS NULL
                ORDER BY pack_key
                """, tenantId, workspaceId, "EVIDENCE_PACK", "pack_key", row -> map(
                "packKey", row.get("pack_key"),
                "name", row.get("name"),
                "status", row.get("status"),
                "scope", row.get("scope"),
                "evidenceRefs", row.get("evidence_refs"),
                "expiresAt", row.get("expires_at") == null ? null : String.valueOf(row.get("expires_at"))
        ));
    }

    private List<Map<String, Object>> loadWorkspaceRows(String sql,
                                                        String tenantId,
                                                        String workspaceId,
                                                        String objectType,
                                                        String keyColumn,
                                                        PayloadBuilder payloadBuilder) {
        List<Map<String, Object>> rows = repository.queryForList(sql, map("tenantId", tenantId, "workspaceId", workspaceId));
        List<Map<String, Object>> objects = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            objects.add(packageObject(objectType, text(row.get(keyColumn)), row.get("version"), payloadBuilder.build(row)));
        }
        return objects;
    }

    private Map<String, Object> packageObject(String type, String key, Object version, Map<String, Object> payload) {
        Map<String, Object> item = map(
                "type", type,
                "key", key,
                "version", version == null ? 0 : version,
                "payload", payload
        );
        item.put("hash", sha256(canonicalJson(payload)));
        return item;
    }

    private void validateManifestEnvelope(Map<String, Object> manifest,
                                          String tenantId,
                                          String workspaceId,
                                          List<Map<String, Object>> findings) {
        if (!SCHEMA_VERSION.equals(text(manifest.get("schemaVersion")))) {
            findings.add(finding("manifest.schema_version", "BLOCK", "Unsupported enterprise package schema version."));
        }
        Map<String, Object> source = safeMap(manifest.get("source"));
        if (!tenantId.equals(text(source.get("tenantId"))) || !workspaceId.equals(text(source.get("workspaceId")))) {
            findings.add(finding("manifest.scope", "BLOCK", "Package source scope does not match the current tenant and workspace."));
        }
        String expected = text(manifest.get("checksum"));
        if (expected.isBlank()) {
            findings.add(finding("manifest.checksum", "BLOCK", "Package checksum is required."));
        } else if (!expected.equals(checksum(manifest))) {
            findings.add(finding("manifest.checksum", "BLOCK", "Package checksum does not match manifest content."));
        }
    }

    private List<Map<String, Object>> readObjects(Object value, List<Map<String, Object>> findings) {
        if (!(value instanceof List<?> items)) {
            findings.add(finding("manifest.objects", "BLOCK", "Package objects must be a list."));
            return List.of();
        }
        List<Map<String, Object>> objects = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                objects.add(safeMap(map));
            } else {
                findings.add(finding("manifest.objects", "BLOCK", "Package object entries must be JSON objects."));
            }
        }
        if (objects.isEmpty()) {
            findings.add(finding("manifest.objects.empty", "BLOCK", "Package manifest contains no objects."));
        }
        if (objects.size() > 500) {
            findings.add(finding("manifest.objects.limit", "BLOCK", "Package manifest exceeds the 500 object dry-run limit."));
        }
        return objects;
    }

    private void validateObject(Map<String, Object> object, List<Map<String, Object>> findings) {
        String type = normalizeType(text(object.get("type")));
        String key = text(object.get("key"));
        if (key.isBlank()) {
            findings.add(finding("object.key", "BLOCK", "Package object key is required."));
        }
        if (DENIED_OBJECT_TYPES.contains(type)) {
            findings.add(finding("object.type.denied", "BLOCK", "Object type is explicitly denied for enterprise packages: " + type));
        } else if (!SUPPORTED_OBJECT_TYPE_SET.contains(type)) {
            findings.add(finding("object.type.unsupported", "BLOCK", "Unsupported enterprise package object type: " + type));
        }
        Map<String, Object> payload = safeMap(object.get("payload"));
        if (payload.isEmpty()) {
            findings.add(finding("object.payload", "BLOCK", "Package object payload is required for " + key + "."));
        }
        String hash = text(object.get("hash"));
        if (hash.isBlank()) {
            findings.add(finding("object.hash", "BLOCK", "Package object hash is required for " + key + "."));
        } else if (!hash.equals(sha256(canonicalJson(payload)))) {
            findings.add(finding("object.hash", "BLOCK", "Package object hash mismatch for " + key + "."));
        }
    }

    private Map<String, Object> diffObject(Map<String, Object> object, String tenantId, String workspaceId, String targetEnvironmentId) {
        String type = normalizeType(text(object.get("type")));
        String key = text(object.get("key"));
        List<Map<String, Object>> rows = switch (type) {
            case "ADMIN_SETTING" -> repository.queryForList("""
                    SELECT config_key, config_value, module_key, category, value_type, scope_type, environment_id,
                           dependency_keys, validation_schema, metadata, description, config_version
                    FROM system_configs
                    WHERE tenant_id = :tenantId
                      AND workspace_id = :workspaceId
                      AND environment_id = :environmentId
                      AND config_key = :key
                      AND deleted_at IS NULL
                    LIMIT 1
                    """, map("tenantId", tenantId, "workspaceId", workspaceId, "environmentId", targetEnvironmentId, "key", key));
            case "FEATURE_CONTROL" -> findByKey("feature_controls", "feature_key", key, tenantId, workspaceId);
            case "GLOBAL_OPERATING_MODEL" -> findByKey("global_operating_models", "model_key", key, tenantId, workspaceId);
            case "DATA_RESIDENCY_POLICY" -> findByKey("tenant_data_residency_policies", "policy_key", key, tenantId, workspaceId);
            case "ENCRYPTION_POLICY" -> findByKey("tenant_encryption_policies", "policy_key", key, tenantId, workspaceId);
            case "EVIDENCE_PACK" -> findByKey("governance_evidence_packs", "pack_key", key, tenantId, workspaceId);
            default -> List.of();
        };
        String action = rows.isEmpty() ? "CREATE" : "UPDATE";
        String reason = rows.isEmpty() ? "Target object does not exist." : "Target object exists and would be compared before live apply.";
        return map(
                "type", type,
                "key", key,
                "action", action,
                "reason", reason,
                "targetEnvironmentId", targetEnvironmentId,
                "liveMutation", false
        );
    }

    private List<Map<String, Object>> findByKey(String table, String keyColumn, String keyValue, String tenantId, String workspaceId) {
        return repository.queryForList(
                "SELECT id FROM " + CorePlatformRepository.safeTable(table)
                        + " WHERE tenant_id = :tenantId AND workspace_id = :workspaceId AND "
                        + CorePlatformRepository.safeColumn(keyColumn)
                        + " = :keyValue AND deleted_at IS NULL LIMIT 1",
                map("tenantId", tenantId, "workspaceId", workspaceId, "keyValue", keyValue)
        );
    }

    private Map<String, Object> requireEnvironment(String environmentId, String tenantId, String workspaceId) {
        Map<String, Object> environment = findEnvironment(environmentId, tenantId, workspaceId);
        if (environment == null) {
            throw new IllegalArgumentException("Environment is not in the current workspace: " + environmentId);
        }
        return environment;
    }

    private Map<String, Object> findEnvironment(String environmentId, String tenantId, String workspaceId) {
        if (blankToNull(environmentId) == null) {
            return null;
        }
        List<Map<String, Object>> rows = repository.queryForList("""
                SELECT e.*, EXISTS (
                    SELECT 1
                    FROM environment_locks l
                    WHERE l.tenant_id = :tenantId
                      AND l.workspace_id = :workspaceId
                      AND l.environment_id = e.id
                      AND l.is_active = TRUE
                      AND (l.expires_at IS NULL OR l.expires_at > NOW())
                      AND l.deleted_at IS NULL
                ) AS active_lock
                FROM environments e
                WHERE e.id = :environmentId
                  AND e.tenant_id = :tenantId
                  AND e.workspace_id = :workspaceId
                  AND e.deleted_at IS NULL
                LIMIT 1
                """, map("environmentId", environmentId.trim(), "tenantId", tenantId, "workspaceId", workspaceId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<String> normalizeObjectTypes(List<String> objectTypes, boolean failOnUnsupported) {
        List<String> source = objectTypes == null || objectTypes.isEmpty() ? SUPPORTED_OBJECT_TYPES : objectTypes;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String objectType : source) {
            String normalizedType = normalizeType(objectType);
            if (DENIED_OBJECT_TYPES.contains(normalizedType) || !SUPPORTED_OBJECT_TYPE_SET.contains(normalizedType)) {
                if (failOnUnsupported) {
                    throw new IllegalArgumentException("Unsupported enterprise package object type: " + objectType);
                }
            } else {
                normalized.add(normalizedType);
            }
        }
        return normalized.stream().toList();
    }

    private List<Map<String, Object>> dependencies(List<Map<String, Object>> objects) {
        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (Map<String, Object> object : objects) {
            Map<String, Object> payload = safeMap(object.get("payload"));
            Object dependencyKeys = payload.get("dependencyKeys");
            if (dependencyKeys instanceof List<?> keys) {
                for (Object key : keys) {
                    if (key != null && !String.valueOf(key).isBlank()) {
                        dependencies.add(map(
                                "fromType", object.get("type"),
                                "fromKey", object.get("key"),
                                "dependencyType", "ADMIN_SETTING",
                                "dependencyKey", String.valueOf(key)
                        ));
                    }
                }
            }
        }
        return dependencies;
    }

    private List<Map<String, Object>> scanUnsafe(Object value) {
        List<Map<String, Object>> findings = new ArrayList<>();
        scanUnsafe(value, "$", null, findings);
        return findings;
    }

    private void scanUnsafe(Object value, String path, String fieldName, List<Map<String, Object>> findings) {
        if (fieldName != null && isSensitiveField(fieldName)) {
            findings.add(finding("package.redaction." + path, "BLOCK", "Sensitive field is not allowed in enterprise package manifests."));
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                scanUnsafe(entry.getValue(), path + "." + key, key, findings);
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                scanUnsafe(list.get(i), path + "[" + i + "]", fieldName, findings);
            }
        } else if (value instanceof String text && isSensitiveValue(text, fieldName)) {
            findings.add(finding("package.redaction." + path, "BLOCK", "Sensitive value is not allowed in enterprise package manifests."));
        }
    }

    private boolean isSensitiveField(String fieldName) {
        String normalized = fieldName.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        if (normalized.endsWith("ref") || normalized.endsWith("refs") || normalized.endsWith("reference") || normalized.endsWith("references")) {
            return false;
        }
        return normalized.contains("password")
                || normalized.contains("accesstoken")
                || normalized.contains("refreshtoken")
                || normalized.contains("apikey")
                || normalized.contains("privatekey")
                || normalized.contains("connectionstring")
                || normalized.contains("cookie")
                || normalized.contains("credential")
                || normalized.contains("rawpayload")
                || normalized.contains("objectkey")
                || normalized.contains("recipient")
                || normalized.equals("email")
                || normalized.contains("emailaddress")
                || normalized.contains("secret");
    }

    private boolean isSensitiveValue(String value, String fieldName) {
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.matches("(?i).+@[a-z0-9.-]+\\.[a-z]{2,}.*")) {
            return true;
        }
        if (lower.contains("-----begin ") || lower.contains("authorization: bearer") || lower.contains("password=")
                || lower.contains("access_token=") || lower.contains("refresh_token=")) {
            return true;
        }
        if (lower.startsWith("jdbc:") || lower.startsWith("postgres://") || lower.startsWith("postgresql://")
                || lower.startsWith("mongodb://") || lower.startsWith("redis://") || lower.startsWith("amqp://")
                || lower.startsWith("smtp://")) {
            return true;
        }
        if (!isReferenceField(fieldName) && (lower.startsWith("s3://") || lower.startsWith("gs://") || lower.startsWith("minio://"))) {
            return true;
        }
        return trimmed.startsWith("eyJ") && trimmed.split("\\.").length == 3;
    }

    private boolean isReferenceField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return normalized.endsWith("ref") || normalized.endsWith("refs") || normalized.endsWith("reference") || normalized.endsWith("references");
    }

    private boolean hasBlockingFinding(List<Map<String, Object>> findings) {
        return findings.stream().anyMatch(finding -> BLOCKING_SEVERITIES.contains(normalizeType(text(finding.get("severity")))));
    }

    private String checksum(Map<String, Object> manifest) {
        Map<String, Object> copy = new LinkedHashMap<>(manifest);
        copy.remove("checksum");
        return sha256(canonicalJson(copy));
    }

    private String canonicalJson(Object value) {
        try {
            ObjectMapper canonical = objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            return canonical.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize enterprise package manifest", e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String workspace(String requestedWorkspaceId) {
        String workspaceId = TenantContext.requireWorkspaceId();
        String requested = blankToNull(requestedWorkspaceId);
        if (requested != null && !workspaceId.equals(requested)) {
            throw new IllegalArgumentException("workspaceId does not match the current workspace");
        }
        return workspaceId;
    }

    private String actor() {
        String actor = TenantContext.getUserId();
        return actor == null || actor.isBlank() ? "SYSTEM" : actor;
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String normalizeType(String value) {
        return value == null ? "" : value.trim().replace("-", "_").replace(" ", "_").toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> finding(String key, String severity, String message) {
        return map("key", key, "severity", severity, "message", message);
    }

    private Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return values;
    }

    @FunctionalInterface
    private interface PayloadBuilder {
        Map<String, Object> build(Map<String, Object> row);
    }
}
