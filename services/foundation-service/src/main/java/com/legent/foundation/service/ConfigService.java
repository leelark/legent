package com.legent.foundation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.cache.service.CacheService;
import com.legent.common.constant.AppConstants;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.SystemConfig;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.mapper.ConfigMapper;
import com.legent.foundation.repository.ConfigRepository;
import com.legent.foundation.repository.ConfigVersionHistoryRepository;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service for managing system configurations.
 * Resolution precedence:
 * ENVIRONMENT > WORKSPACE > TENANT > GLOBAL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_TENANT = "TENANT";
    public static final String SCOPE_WORKSPACE = "WORKSPACE";
    public static final String SCOPE_ENVIRONMENT = "ENVIRONMENT";

    private static final Duration CACHE_TTL = Duration.ofSeconds(AppConstants.CACHE_CONFIG_TTL_SECONDS);

    private final ConfigRepository configRepository;
    private final ConfigVersionHistoryRepository configVersionHistoryRepository;
    private final ConfigMapper configMapper;
    private final CacheService cacheService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ConfigDto.Response resolveConfig(String configKey) {
        String tenantId = normalize(TenantContext.getTenantId());
        String workspaceId = normalize(TenantContext.getWorkspaceId());
        String environmentId = normalize(TenantContext.getEnvironmentId());
        String cacheKey = generateScopedCacheKey(tenantId, workspaceId, environmentId, configKey);

        return cacheService.get(cacheKey, ConfigDto.Response.class)
                .orElseGet(() -> {
                    List<SystemConfig> configs = configRepository.findByKeyWithFallback(
                            configKey, tenantId, workspaceId, environmentId);
                    if (configs.isEmpty()) {
                        throw new NotFoundException("SystemConfig", configKey);
                    }
                    ConfigDto.Response response = configMapper.toResponse(configs.get(0));
                    cacheService.set(cacheKey, response, CACHE_TTL);
                    return response;
                });
    }

    @Transactional(readOnly = true)
    public Page<ConfigDto.Response> listConfigs(String tenantId, Pageable pageable) {
        Page<SystemConfig> configs = (tenantId != null)
                ? configRepository.findByTenantId(tenantId, pageable)
                : configRepository.findGlobalConfigs(pageable);
        return configs.map(configMapper::toResponse);
    }

    @Transactional
    public ConfigDto.Response createConfig(String tenantId, ConfigDto.CreateRequest request) {
        String resolvedTenantId = normalize(tenantId);
        String scope = resolveScope(request.getScopeType(), request.getWorkspaceId(), request.getEnvironmentId(), resolvedTenantId);
        String workspaceId = scopedWorkspace(scope, request.getWorkspaceId());
        String environmentId = scopedEnvironment(scope, request.getEnvironmentId());
        String effectiveTenantId = SCOPE_GLOBAL.equals(scope) ? null : resolvedTenantId;

        if (configRepository.existsByScope(effectiveTenantId, workspaceId, environmentId, request.getConfigKey())) {
            throw new ConflictException("SystemConfig", "configKey", request.getConfigKey());
        }

        SystemConfig entity = configMapper.toEntity(request);
        hydrateConfigScope(entity, request, scope, effectiveTenantId, workspaceId, environmentId);

        SystemConfig saved = configRepository.save(entity);
        recordVersion(saved, com.legent.foundation.domain.ConfigVersionHistory.ChangeType.CREATE);
        invalidateCache(saved.getTenantId(), saved.getConfigKey());
        publishConfigUpdatedEvent(saved.getTenantId(), saved.getWorkspaceId(), saved.getEnvironmentId(), saved.getConfigKey(), "CREATED");
        log.info("Config created: key={}, tenantId={}, workspaceId={}, environmentId={}",
                saved.getConfigKey(), saved.getTenantId(), saved.getWorkspaceId(), saved.getEnvironmentId());

        return configMapper.toResponse(saved);
    }

    @Transactional
    public ConfigDto.Response updateConfig(String id, ConfigDto.UpdateRequest request) {
        SystemConfig existing = configRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SystemConfig", id));

        existing.setConfigValue(request.getConfigValue());
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getCategory() != null) {
            existing.setCategory(request.getCategory());
        }
        if (request.getModuleKey() != null) {
            existing.setModuleKey(defaultModule(request.getModuleKey(), existing.getCategory()));
        }
        if (request.getDependencyKeys() != null) {
            existing.setDependencyKeys(parseDependencyKeys(request.getDependencyKeys()));
        }
        if (request.getValidationSchema() != null) {
            existing.setValidationSchema(parseObjectMap(request.getValidationSchema()));
        }
        if (request.getMetadata() != null) {
            existing.setMetadata(parseObjectMap(request.getMetadata()));
        }
        existing.setLastModifiedBy(normalize(TenantContext.getUserId()));

        SystemConfig saved = configRepository.save(existing);
        recordVersion(saved, com.legent.foundation.domain.ConfigVersionHistory.ChangeType.UPDATE);
        invalidateCache(saved.getTenantId(), saved.getConfigKey());
        publishConfigUpdatedEvent(saved.getTenantId(), saved.getWorkspaceId(), saved.getEnvironmentId(), saved.getConfigKey(), "UPDATED");
        log.info("Config updated: key={}, id={}", saved.getConfigKey(), id);

        return configMapper.toResponse(saved);
    }

    @Transactional
    public void deleteConfig(String id) {
        SystemConfig existing = configRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SystemConfig", id));
        existing.softDelete();
        configRepository.save(existing);
        recordVersion(existing, com.legent.foundation.domain.ConfigVersionHistory.ChangeType.DELETE);
        invalidateCache(existing.getTenantId(), existing.getConfigKey());
        log.info("Config soft-deleted: key={}, id={}", existing.getConfigKey(), id);
    }

    @Transactional
    public ConfigDto.Response upsertConfig(String tenantId, String workspaceId, String environmentId, ConfigDto.CreateRequest request) {
        String resolvedTenantId = normalize(tenantId);
        String resolvedWorkspaceId = normalize(workspaceId != null ? workspaceId : request.getWorkspaceId());
        String resolvedEnvironmentId = normalize(environmentId != null ? environmentId : request.getEnvironmentId());
        String scope = resolveScope(request.getScopeType(), resolvedWorkspaceId, resolvedEnvironmentId, resolvedTenantId);

        String scopedWorkspace = scopedWorkspace(scope, resolvedWorkspaceId);
        String scopedEnvironment = scopedEnvironment(scope, resolvedEnvironmentId);
        String effectiveTenantId = SCOPE_GLOBAL.equals(scope) ? null : resolvedTenantId;

        SystemConfig config = configRepository
                .findByScope(effectiveTenantId, scopedWorkspace, scopedEnvironment, request.getConfigKey())
                .orElseGet(SystemConfig::new);
        boolean isNew = config.getId() == null;
        if (isNew) {
            config.setConfigKey(request.getConfigKey());
        }

        hydrateConfigScope(config, request, scope, effectiveTenantId, scopedWorkspace, scopedEnvironment);

        SystemConfig saved = configRepository.save(config);
        recordVersion(saved, isNew ? com.legent.foundation.domain.ConfigVersionHistory.ChangeType.CREATE : com.legent.foundation.domain.ConfigVersionHistory.ChangeType.UPDATE);
        invalidateCache(saved.getTenantId(), saved.getConfigKey());
        publishConfigUpdatedEvent(saved.getTenantId(), saved.getWorkspaceId(), saved.getEnvironmentId(), saved.getConfigKey(), isNew ? "CREATED" : "UPDATED");
        return configMapper.toResponse(saved);
    }

    private void hydrateConfigScope(SystemConfig entity,
                                    ConfigDto.CreateRequest request,
                                    String scope,
                                    String tenantId,
                                    String workspaceId,
                                    String environmentId) {
        entity.setTenantId(tenantId);
        entity.setWorkspaceId(workspaceId);
        entity.setEnvironmentId(environmentId);
        entity.setScopeType(SystemConfig.ScopeType.valueOf(scope));
        entity.setConfigValue(request.getConfigValue());
        if (request.getValueType() != null) {
            entity.setValueType(SystemConfig.ValueType.valueOf(request.getValueType().toUpperCase(Locale.ROOT)));
        }
        entity.setCategory(request.getCategory() == null ? "GENERAL" : request.getCategory());
        entity.setModuleKey(defaultModule(request.getModuleKey(), entity.getCategory()));
        entity.setDescription(request.getDescription());
        entity.setDependencyKeys(parseDependencyKeys(request.getDependencyKeys()));
        entity.setValidationSchema(parseObjectMap(request.getValidationSchema()));
        entity.setMetadata(parseObjectMap(request.getMetadata()));
        entity.setLastModifiedBy(normalize(TenantContext.getUserId()));
    }

    private void invalidateCache(String tenantId, String configKey) {
        String tenantPart = tenantId == null ? "global" : tenantId;
        cacheService.deleteByPattern(AppConstants.CACHE_CONFIG_PREFIX + tenantPart + ":*:" + configKey);
        cacheService.deleteByPattern(AppConstants.CACHE_CONFIG_PREFIX + "global:*:" + configKey);
    }

    private String generateScopedCacheKey(String tenantId, String workspaceId, String environmentId, String configKey) {
        return AppConstants.CACHE_CONFIG_PREFIX
                + (tenantId == null ? "global" : tenantId)
                + ":" + (workspaceId == null ? "-" : workspaceId)
                + ":" + (environmentId == null ? "-" : environmentId)
                + ":" + configKey;
    }

    private void publishConfigUpdatedEvent(String tenantId, String workspaceId, String environmentId, String configKey, String action) {
        Map<String, String> payload = new HashMap<>();
        payload.put("configKey", configKey);
        payload.put("action", action);
        if (workspaceId != null) {
            payload.put("workspaceId", workspaceId);
        }
        if (environmentId != null) {
            payload.put("environmentId", environmentId);
        }

        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_CONFIG_UPDATED,
                tenantId,
                "foundation-service",
                payload);
        eventPublisher.publish(AppConstants.TOPIC_CONFIG_UPDATED, envelope);
    }

    private void recordVersion(SystemConfig config, com.legent.foundation.domain.ConfigVersionHistory.ChangeType changeType) {
        try {
            com.legent.foundation.domain.ConfigVersionHistory entry = new com.legent.foundation.domain.ConfigVersionHistory();
            entry.setTenantId(config.getTenantId());
            entry.setConfigKey(config.getConfigKey());
            entry.setConfigValue(config.getConfigValue());
            entry.setValueType(config.getValueType() != null ? config.getValueType().name() : "STRING");
            entry.setCategory(config.getCategory());
            entry.setDescription(config.getDescription());
            entry.setEncrypted(config.isEncrypted());
            Integer maxVersion = configVersionHistoryRepository.findMaxVersionByTenantIdAndConfigKey(config.getTenantId(), config.getConfigKey());
            entry.setVersion((maxVersion == null ? 0 : maxVersion) + 1);
            entry.setChangeType(changeType.name());
            entry.setChangedBy(normalize(TenantContext.getUserId()));
            configVersionHistoryRepository.save(entry);
            config.setConfigVersion(entry.getVersion());
        } catch (Exception ex) {
            log.warn("Failed to record config version for key={}: {}", config.getConfigKey(), ex.getMessage());
        }
    }

    private String resolveScope(String requestedScope, String workspaceId, String environmentId, String tenantId) {
        if (requestedScope != null && !requestedScope.isBlank()) {
            return requestedScope.trim().toUpperCase(Locale.ROOT);
        }
        if (environmentId != null && !environmentId.isBlank()) {
            return SCOPE_ENVIRONMENT;
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            return SCOPE_WORKSPACE;
        }
        if (tenantId != null && !tenantId.isBlank()) {
            return SCOPE_TENANT;
        }
        return SCOPE_GLOBAL;
    }

    private String scopedWorkspace(String scope, String workspaceId) {
        if (SCOPE_WORKSPACE.equals(scope) || SCOPE_ENVIRONMENT.equals(scope)) {
            return normalize(workspaceId);
        }
        return null;
    }

    private String scopedEnvironment(String scope, String environmentId) {
        if (SCOPE_ENVIRONMENT.equals(scope)) {
            return normalize(environmentId);
        }
        return null;
    }

    private String defaultModule(String moduleKey, String category) {
        String module = normalize(moduleKey);
        if (module != null) {
            return module.toLowerCase(Locale.ROOT);
        }
        String fallback = normalize(category);
        return fallback == null ? "system" : fallback.toLowerCase(Locale.ROOT);
    }

    private List<String> parseDependencyKeys(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return new ArrayList<>();
        }
        try {
            List<String> parsed = objectMapper.readValue(normalized, new TypeReference<List<String>>() { });
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception ex) {
            log.warn("Invalid dependencyKeys JSON; using empty list: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private Map<String, Object> parseObjectMap(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(normalized, new TypeReference<Map<String, Object>>() { });
            return parsed == null ? new HashMap<>() : new HashMap<>(parsed);
        } catch (Exception ex) {
            log.warn("Invalid JSON object payload; using empty object: {}", ex.getMessage());
            return new HashMap<>();
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
