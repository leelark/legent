package com.legent.foundation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.domain.ConfigVersionHistory;
import com.legent.foundation.domain.SystemConfig;
import com.legent.foundation.dto.AdminSettingsDto;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.repository.ConfigRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSettingsService {

    private final ConfigService configService;
    private final ConfigRepository configRepository;
    private final ConfigVersioningService configVersioningService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<AdminSettingsDto.Entry> listSettings(String module, String category, String scope) {
        String tenantId = TenantContext.getTenantId();
        String workspaceId = TenantContext.getWorkspaceId();
        String environmentId = TenantContext.getEnvironmentId();
        String moduleFilter = normalize(module);
        String categoryFilter = normalize(category);
        String scopeFilter = normalizeUpper(scope);

        return configRepository.findAll().stream()
                .filter(config -> config.getDeletedAt() == null)
                .filter(config -> tenantMatch(config, tenantId))
                .filter(config -> moduleFilter == null || moduleFilter.equalsIgnoreCase(config.getModuleKey()))
                .filter(config -> categoryFilter == null || categoryFilter.equalsIgnoreCase(config.getCategory()))
                .filter(config -> scopeFilter == null || (config.getScopeType() != null && scopeFilter.equals(config.getScopeType().name())))
                .filter(config -> isVisibleForContext(config, workspaceId, environmentId))
                .map(this::toEntry)
                .sorted((a, b) -> {
                    int moduleCompare = safe(a.getModule()).compareToIgnoreCase(safe(b.getModule()));
                    if (moduleCompare != 0) {
                        return moduleCompare;
                    }
                    return safe(a.getKey()).compareToIgnoreCase(safe(b.getKey()));
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AdminSettingsDto.ValidateResponse validate(AdminSettingsDto.ApplyRequest request) {
        List<String> errors = new ArrayList<>();
        String key = normalize(request.getKey());
        String value = request.getValue();
        String type = normalizeUpper(request.getType());
        String scope = normalizeUpper(request.getScope());
        String workspaceId = normalize(request.getWorkspaceId() != null ? request.getWorkspaceId() : TenantContext.getWorkspaceId());
        String environmentId = normalize(request.getEnvironmentId() != null ? request.getEnvironmentId() : TenantContext.getEnvironmentId());
        String tenantId = normalize(TenantContext.getTenantId());

        if (key == null) {
            errors.add("Setting key is required");
        }
        if (value == null) {
            errors.add("Setting value is required");
        }
        if (scope == null) {
            scope = inferScope(workspaceId, environmentId, tenantId);
        }
        if (ConfigService.SCOPE_WORKSPACE.equals(scope) && workspaceId == null) {
            errors.add("workspaceId required for WORKSPACE scope");
        }
        if (ConfigService.SCOPE_ENVIRONMENT.equals(scope) && (workspaceId == null || environmentId == null)) {
            errors.add("workspaceId and environmentId required for ENVIRONMENT scope");
        }

        if (value != null && type != null) {
            validateType(type, value, errors);
        }

        if (request.getDependencyKeys() != null) {
            for (String dependency : request.getDependencyKeys()) {
                String depKey = normalize(dependency);
                if (depKey == null) {
                    continue;
                }
                List<SystemConfig> found = configRepository.findByKeyWithFallback(depKey, tenantId, workspaceId, environmentId);
                if (found.isEmpty()) {
                    errors.add("Missing dependency config: " + depKey);
                }
            }
        }

        return new AdminSettingsDto.ValidateResponse(errors.isEmpty(), errors);
    }

    @Transactional
    public AdminSettingsDto.Entry apply(AdminSettingsDto.ApplyRequest request) {
        AdminSettingsDto.ValidateResponse validation = validate(request);
        if (!validation.isValid()) {
            throw new IllegalArgumentException(String.join("; ", validation.getErrors()));
        }

        String tenantId = TenantContext.getTenantId();
        String workspaceId = normalize(request.getWorkspaceId() != null ? request.getWorkspaceId() : TenantContext.getWorkspaceId());
        String environmentId = normalize(request.getEnvironmentId() != null ? request.getEnvironmentId() : TenantContext.getEnvironmentId());

        ConfigDto.CreateRequest upsert = ConfigDto.CreateRequest.builder()
                .configKey(request.getKey())
                .configValue(request.getValue())
                .valueType(defaultType(request.getType(), request.getValue()))
                .category(defaultCategory(request.getCategory(), request.getModule()))
                .moduleKey(defaultModule(request.getModule()))
                .scopeType(normalizeUpper(request.getScope()))
                .workspaceId(workspaceId)
                .environmentId(environmentId)
                .dependencyKeys(toJson(request.getDependencyKeys() == null ? Collections.emptyList() : request.getDependencyKeys()))
                .validationSchema(toJson(request.getValidationSchema() == null ? Collections.emptyMap() : request.getValidationSchema()))
                .metadata(toJson(request.getMetadata() == null ? Collections.emptyMap() : request.getMetadata()))
                .description(request.getDescription())
                .build();

        ConfigDto.Response saved = configService.upsertConfig(tenantId, workspaceId, environmentId, upsert);
        return toEntry(saved);
    }

    @Transactional
    public AdminSettingsDto.Entry reset(AdminSettingsDto.ResetRequest request) {
        String tenantId = TenantContext.getTenantId();
        String scope = normalizeUpper(request.getScope());
        String workspaceId = normalize(request.getWorkspaceId() != null ? request.getWorkspaceId() : TenantContext.getWorkspaceId());
        String environmentId = normalize(request.getEnvironmentId() != null ? request.getEnvironmentId() : TenantContext.getEnvironmentId());

        String effectiveScope = scope == null ? inferScope(workspaceId, environmentId, tenantId) : scope;
        String scopedWorkspace = ConfigService.SCOPE_WORKSPACE.equals(effectiveScope) || ConfigService.SCOPE_ENVIRONMENT.equals(effectiveScope)
                ? workspaceId : null;
        String scopedEnvironment = ConfigService.SCOPE_ENVIRONMENT.equals(effectiveScope) ? environmentId : null;
        String effectiveTenant = ConfigService.SCOPE_GLOBAL.equals(effectiveScope) ? null : tenantId;

        SystemConfig existing = configRepository
                .findByScope(effectiveTenant, scopedWorkspace, scopedEnvironment, request.getKey())
                .orElseThrow(() -> new IllegalArgumentException("Setting not found for reset"));

        configService.deleteConfig(existing.getId());
        ConfigDto.Response resolved = configService.resolveConfig(request.getKey());
        return toEntry(resolved);
    }

    @Transactional(readOnly = true)
    public AdminSettingsDto.ImpactResponse impact(AdminSettingsDto.ApplyRequest request) {
        String module = defaultModule(request.getModule());
        List<String> impacted = new ArrayList<>(impactMap().getOrDefault(module, List.of("system")));
        if (!impacted.contains(module)) {
            impacted.add(0, module);
        }

        List<String> notices = new ArrayList<>();
        notices.add("Config propagated via config.updated event.");
        if (request.getDependencyKeys() != null && !request.getDependencyKeys().isEmpty()) {
            notices.add("Dependency chain size: " + request.getDependencyKeys().size());
        }
        if (ConfigService.SCOPE_ENVIRONMENT.equalsIgnoreCase(request.getScope())) {
            notices.add("Environment scope overrides workspace/tenant/global.");
        }

        return AdminSettingsDto.ImpactResponse.builder()
                .key(request.getKey())
                .module(module)
                .impactedModules(impacted)
                .notices(notices)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ConfigVersionHistory> history(String key) {
        String tenantId = TenantContext.getTenantId();
        if (normalize(key) == null) {
            return configVersioningService
                    .getTenantVersionHistory(tenantId, org.springframework.data.domain.PageRequest.of(0, 200))
                    .getContent();
        }
        return configVersioningService.getConfigVersionHistory(tenantId, key);
    }

    @Transactional
    public AdminSettingsDto.Entry rollback(String key, int version) {
        String tenantId = TenantContext.getTenantId();
        ConfigDto.Response rolled = configVersioningService.rollbackConfig(tenantId, key, version);
        return toEntry(rolled);
    }

    private AdminSettingsDto.Entry toEntry(SystemConfig config) {
        return AdminSettingsDto.Entry.builder()
                .id(config.getId())
                .key(config.getConfigKey())
                .module(config.getModuleKey())
                .category(config.getCategory())
                .type(config.getValueType() != null ? config.getValueType().name() : "STRING")
                .scope(config.getScopeType() != null ? config.getScopeType().name() : ConfigService.SCOPE_TENANT)
                .tenantId(config.getTenantId())
                .workspaceId(config.getWorkspaceId())
                .environmentId(config.getEnvironmentId())
                .value(config.getConfigValue())
                .version(config.getConfigVersion())
                .updatedBy(config.getLastModifiedBy())
                .updatedAt(config.getUpdatedAt())
                .dependencyKeys(parseList(config.getDependencyKeys()))
                .validationSchema(toJson(config.getValidationSchema()))
                .metadata(toJson(config.getMetadata()))
                .description(config.getDescription())
                .build();
    }

    private AdminSettingsDto.Entry toEntry(ConfigDto.Response config) {
        return AdminSettingsDto.Entry.builder()
                .id(config.getId())
                .key(config.getConfigKey())
                .module(config.getModuleKey())
                .category(config.getCategory())
                .type(config.getValueType())
                .scope(config.getScopeType())
                .tenantId(config.getTenantId())
                .workspaceId(config.getWorkspaceId())
                .environmentId(config.getEnvironmentId())
                .value(config.getConfigValue())
                .version(config.getConfigVersion())
                .updatedBy(config.getLastModifiedBy())
                .updatedAt(config.getUpdatedAt())
                .dependencyKeys(parseList(config.getDependencyKeys()))
                .validationSchema(config.getValidationSchema())
                .metadata(config.getMetadata())
                .description(config.getDescription())
                .build();
    }

    private Map<String, List<String>> impactMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("delivery", List.of("campaign", "automation", "audience"));
        map.put("campaign", List.of("delivery", "automation", "analytics"));
        map.put("automation", List.of("campaign", "delivery", "audience"));
        map.put("audience", List.of("campaign", "template", "analytics"));
        map.put("template", List.of("campaign", "delivery"));
        map.put("analytics", List.of("campaign", "audience", "delivery", "automation"));
        map.put("security", List.of("system", "campaign", "delivery", "automation"));
        map.put("system", List.of("audience", "template", "campaign", "automation", "delivery", "analytics"));
        return map;
    }

    private String defaultType(String type, String value) {
        String normalized = normalizeUpper(type);
        if (normalized != null) {
            return normalized;
        }
        String v = value == null ? "" : value.trim();
        if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
            return "BOOLEAN";
        }
        if (v.matches("^-?\\d+$")) {
            return "INTEGER";
        }
        if (v.startsWith("{") || v.startsWith("[")) {
            return "JSON";
        }
        return "STRING";
    }

    private String defaultCategory(String category, String module) {
        String c = normalize(category);
        if (c != null) {
            return c.toUpperCase(Locale.ROOT);
        }
        String m = defaultModule(module);
        return m.toUpperCase(Locale.ROOT);
    }

    private String defaultModule(String module) {
        String normalized = normalize(module);
        return normalized == null ? "system" : normalized.toLowerCase(Locale.ROOT);
    }

    private String inferScope(String workspaceId, String environmentId, String tenantId) {
        if (normalize(environmentId) != null) {
            return ConfigService.SCOPE_ENVIRONMENT;
        }
        if (normalize(workspaceId) != null) {
            return ConfigService.SCOPE_WORKSPACE;
        }
        if (normalize(tenantId) != null) {
            return ConfigService.SCOPE_TENANT;
        }
        return ConfigService.SCOPE_GLOBAL;
    }

    private boolean tenantMatch(SystemConfig config, String tenantId) {
        if (config.getTenantId() == null) {
            return true;
        }
        return Objects.equals(config.getTenantId(), tenantId);
    }

    private boolean isVisibleForContext(SystemConfig config, String workspaceId, String environmentId) {
        if (config.getScopeType() == null) {
            return true;
        }
        return switch (config.getScopeType()) {
            case GLOBAL, TENANT -> true;
            case WORKSPACE -> Objects.equals(config.getWorkspaceId(), workspaceId);
            case ENVIRONMENT -> Objects.equals(config.getWorkspaceId(), workspaceId)
                    && Objects.equals(config.getEnvironmentId(), environmentId);
        };
    }

    private void validateType(String type, String value, List<String> errors) {
        try {
            switch (type) {
                case "BOOLEAN" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        errors.add("BOOLEAN value must be true|false");
                    }
                }
                case "INTEGER" -> Integer.parseInt(value);
                case "DECIMAL" -> Double.parseDouble(value);
                case "JSON" -> objectMapper.readTree(value);
                default -> {
                }
            }
        } catch (Exception ex) {
            errors.add("Value does not match type " + type + ": " + ex.getMessage());
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    private List<String> parseList(String value) {
        if (normalize(value) == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> parseList(List<String> value) {
        if (value == null) {
            return List.of();
        }
        return value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
