package com.legent.foundation.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.ConfigVersionHistory;
import com.legent.foundation.domain.SystemConfig;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.repository.ConfigRepository;
import com.legent.foundation.repository.ConfigVersionHistoryRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for configuration versioning and rollback operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigVersioningService {

    private final ConfigVersionHistoryRepository versionHistoryRepository;
    private final ConfigRepository configRepository;
    private final ConfigService configService;
    private final AuditService auditService;

    /**
     * Get version history for a specific config key.
     */
    @Transactional(readOnly = true)
    public List<ConfigVersionHistory> getConfigVersionHistory(
            String tenantId, String workspaceId, String environmentId, String configKey) {
        return versionHistoryRepository.findByExactScopeAndConfigKeyOrderByVersionDesc(
                tenantId, workspaceId, environmentId, configKey);
    }

    /**
     * Get all version history for a tenant.
     */
    @Transactional(readOnly = true)
    public Page<ConfigVersionHistory> getTenantVersionHistory(
            String tenantId, String workspaceId, String environmentId, Pageable pageable) {
        return versionHistoryRepository.findByExactScopeOrderByChangedAtDesc(
                tenantId, workspaceId, environmentId, pageable);
    }

    /**
     * Get a specific version of a config.
     */
    @Transactional(readOnly = true)
    public ConfigVersionHistory getConfigVersion(
            String tenantId, String workspaceId, String environmentId, String configKey, int version) {
        return versionHistoryRepository.findByExactScopeAndConfigKeyAndVersion(
                        tenantId, workspaceId, environmentId, configKey, version)
                .orElseThrow(() -> new NotFoundException("ConfigVersion",
                        scopeLabel(tenantId, workspaceId, environmentId, configKey, version)));
    }

    /**
     * Rollback a config to a specific version.
     */
    @Transactional
    public ConfigDto.Response rollbackConfig(
            String tenantId, String workspaceId, String environmentId, String configKey, int targetVersion) {
        String currentUser = TenantContext.getUserId();

        // Get the target version from history
        ConfigVersionHistory targetVersionHistory = versionHistoryRepository
                .findByExactScopeAndConfigKeyAndVersion(
                        tenantId, workspaceId, environmentId, configKey, targetVersion)
                .orElseThrow(() -> new NotFoundException("ConfigVersion",
                        scopeLabel(tenantId, workspaceId, environmentId, configKey, targetVersion)));

        // Find current config
        Optional<SystemConfig> currentConfigOpt = configRepository
                .findByScope(tenantId, workspaceId, environmentId, configKey);

        Integer maxVersion = versionHistoryRepository.findMaxVersionByExactScopeAndConfigKey(
                tenantId, workspaceId, environmentId, configKey);
        int previousVersion = maxVersion == null ? 0 : maxVersion;
        int newVersion = previousVersion + 1;
        SystemConfig restoredConfig;
        if (currentConfigOpt.isPresent()) {
            SystemConfig currentConfig = currentConfigOpt.get();

            // Restore values from history
            currentConfig.setConfigValue(targetVersionHistory.getConfigValue());
            currentConfig.setValueType(SystemConfig.ValueType.valueOf(targetVersionHistory.getValueType()));
            currentConfig.setCategory(targetVersionHistory.getCategory());
            currentConfig.setDescription(targetVersionHistory.getDescription());
            currentConfig.setEncrypted(targetVersionHistory.isEncrypted());
            currentConfig.setConfigVersion(newVersion);
            currentConfig.setLastModifiedBy(currentUser);
            currentConfig.setUpdatedAt(Instant.now());

            restoredConfig = configRepository.save(currentConfig);
        } else {
            // Create new config from history
            SystemConfig newConfig = new SystemConfig();
            newConfig.setTenantId(tenantId);
            newConfig.setWorkspaceId(workspaceId);
            newConfig.setEnvironmentId(environmentId);
            newConfig.setScopeType(resolveScopeType(tenantId, workspaceId, environmentId));
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(targetVersionHistory.getConfigValue());
            newConfig.setValueType(SystemConfig.ValueType.valueOf(targetVersionHistory.getValueType()));
            newConfig.setCategory(targetVersionHistory.getCategory());
            newConfig.setDescription(targetVersionHistory.getDescription());
            newConfig.setEncrypted(targetVersionHistory.isEncrypted());
            newConfig.setConfigVersion(newVersion);
            newConfig.setLastModifiedBy(currentUser);
            restoredConfig = configRepository.save(newConfig);
        }

        // Create rollback history entry
        ConfigVersionHistory rollbackEntry = new ConfigVersionHistory();
        rollbackEntry.setTenantId(tenantId);
        rollbackEntry.setWorkspaceId(workspaceId);
        rollbackEntry.setEnvironmentId(environmentId);
        rollbackEntry.setConfigKey(configKey);
        rollbackEntry.setConfigValue(targetVersionHistory.getConfigValue());
        rollbackEntry.setValueType(targetVersionHistory.getValueType());
        rollbackEntry.setCategory(targetVersionHistory.getCategory());
        rollbackEntry.setDescription(targetVersionHistory.getDescription());
        rollbackEntry.setEncrypted(targetVersionHistory.isEncrypted());
        rollbackEntry.setVersion(newVersion);
        rollbackEntry.setChangeType(ConfigVersionHistory.ChangeType.ROLLBACK.name());
        rollbackEntry.setChangedBy(currentUser);
        rollbackEntry.setRollbackToVersion(targetVersion);
        versionHistoryRepository.save(rollbackEntry);

        log.info("Config rolled back: tenant={}, workspace={}, environment={}, key={}, from version {} to {}",
                tenantId, workspaceId, environmentId, configKey, previousVersion, targetVersion);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("tenantId", tenantId);
        auditDetails.put("workspaceId", workspaceId);
        auditDetails.put("environmentId", environmentId);
        auditDetails.put("fromVersion", previousVersion);
        auditDetails.put("toVersion", targetVersion);
        auditDetails.put("newVersion", newVersion);
        auditService.log("CONFIG_ROLLBACK", "SystemConfig", configKey,
                auditDetails);

        return configService.finalizeRollback(restoredConfig);
    }

    /**
     * Record a config change in version history.
     * Called by ConfigService when configs are created/updated.
     */
    @Transactional
    public void recordConfigChange(SystemConfig config, ConfigVersionHistory.ChangeType changeType) {
        String currentUser = TenantContext.getUserId();

        // Get next version number
        Integer maxVersion = versionHistoryRepository.findMaxVersionByExactScopeAndConfigKey(
                config.getTenantId(), config.getWorkspaceId(), config.getEnvironmentId(), config.getConfigKey());
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;

        // Update config version
        config.setConfigVersion(nextVersion);

        // Create history entry
        ConfigVersionHistory history = new ConfigVersionHistory();
        history.setTenantId(config.getTenantId());
        history.setWorkspaceId(config.getWorkspaceId());
        history.setEnvironmentId(config.getEnvironmentId());
        history.setConfigKey(config.getConfigKey());
        history.setConfigValue(config.getConfigValue());
        history.setValueType(config.getValueType().name());
        history.setCategory(config.getCategory());
        history.setDescription(config.getDescription());
        history.setEncrypted(config.isEncrypted());
        history.setVersion(nextVersion);
        history.setChangeType(changeType.name());
        history.setChangedBy(currentUser);
        versionHistoryRepository.save(history);

        log.debug("Config version recorded: tenant={}, workspace={}, environment={}, key={}, version={}, changeType={}",
                config.getTenantId(), config.getWorkspaceId(), config.getEnvironmentId(),
                config.getConfigKey(), nextVersion, changeType);
    }

    /**
     * Compare two versions of a config.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(
            String tenantId, String workspaceId, String environmentId, String configKey, int version1, int version2) {
        ConfigVersionHistory v1 = getConfigVersion(tenantId, workspaceId, environmentId, configKey, version1);
        ConfigVersionHistory v2 = getConfigVersion(tenantId, workspaceId, environmentId, configKey, version2);

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("tenantId", tenantId);
        comparison.put("workspaceId", workspaceId);
        comparison.put("environmentId", environmentId);
        comparison.put("configKey", configKey);
        comparison.put("version1", version1);
        comparison.put("version2", version2);
        comparison.put("value1", v1.getConfigValue());
        comparison.put("value2", v2.getConfigValue());
        comparison.put("areEqual", Objects.equals(v1.getConfigValue(), v2.getConfigValue()));
        comparison.put("changedAt1", v1.getChangedAt());
        comparison.put("changedAt2", v2.getChangedAt());
        comparison.put("changedBy1", v1.getChangedBy());
        comparison.put("changedBy2", v2.getChangedBy());
        return comparison;
    }

    private SystemConfig.ScopeType resolveScopeType(String tenantId, String workspaceId, String environmentId) {
        if (environmentId != null) {
            return SystemConfig.ScopeType.ENVIRONMENT;
        }
        if (workspaceId != null) {
            return SystemConfig.ScopeType.WORKSPACE;
        }
        if (tenantId != null) {
            return SystemConfig.ScopeType.TENANT;
        }
        return SystemConfig.ScopeType.GLOBAL;
    }

    private String scopeLabel(String tenantId, String workspaceId, String environmentId, String configKey, int version) {
        return "tenant=" + tenantId
                + "/workspace=" + workspaceId
                + "/environment=" + environmentId
                + "/" + configKey
                + " v" + version;
    }
}
