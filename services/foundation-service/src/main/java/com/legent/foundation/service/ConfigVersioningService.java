package com.legent.foundation.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    public List<ConfigVersionHistory> getConfigVersionHistory(String tenantId, String configKey) {
        return versionHistoryRepository.findByTenantIdAndConfigKeyOrderByVersionDesc(tenantId, configKey);
    }

    /**
     * Get all version history for a tenant.
     */
    @Transactional(readOnly = true)
    public Page<ConfigVersionHistory> getTenantVersionHistory(String tenantId, Pageable pageable) {
        return versionHistoryRepository.findByTenantIdOrderByChangedAtDesc(tenantId, pageable);
    }

    /**
     * Get a specific version of a config.
     */
    @Transactional(readOnly = true)
    public ConfigVersionHistory getConfigVersion(String tenantId, String configKey, int version) {
        return versionHistoryRepository.findByTenantIdAndConfigKeyAndVersion(tenantId, configKey, version)
                .orElseThrow(() -> new NotFoundException("ConfigVersion",
                        tenantId + "/" + configKey + " v" + version));
    }

    /**
     * Rollback a config to a specific version.
     */
    @Transactional
    public ConfigDto.Response rollbackConfig(String tenantId, String configKey, int targetVersion) {
        String currentUser = TenantContext.getUserId();

        // Get the target version from history
        ConfigVersionHistory targetVersionHistory = versionHistoryRepository
                .findByTenantIdAndConfigKeyAndVersion(tenantId, configKey, targetVersion)
                .orElseThrow(() -> new NotFoundException("ConfigVersion",
                        tenantId + "/" + configKey + " v" + targetVersion));

        // Find current config
        Optional<SystemConfig> currentConfigOpt = configRepository
                .findByTenantIdAndConfigKey(tenantId, configKey);

        int previousVersion = 1;
        if (currentConfigOpt.isPresent()) {
            SystemConfig currentConfig = currentConfigOpt.get();
            previousVersion = currentConfig.getConfigVersion() != null ? currentConfig.getConfigVersion() : 1;

            // Restore values from history
            currentConfig.setConfigValue(targetVersionHistory.getConfigValue());
            currentConfig.setValueType(SystemConfig.ValueType.valueOf(targetVersionHistory.getValueType()));
            currentConfig.setCategory(targetVersionHistory.getCategory());
            currentConfig.setDescription(targetVersionHistory.getDescription());
            currentConfig.setEncrypted(targetVersionHistory.isEncrypted());
            currentConfig.setConfigVersion(previousVersion + 1);
            currentConfig.setLastModifiedBy(currentUser);
            currentConfig.setUpdatedAt(Instant.now());

            configRepository.save(currentConfig);
        } else {
            // Create new config from history
            SystemConfig newConfig = new SystemConfig();
            newConfig.setTenantId(tenantId);
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(targetVersionHistory.getConfigValue());
            newConfig.setValueType(SystemConfig.ValueType.valueOf(targetVersionHistory.getValueType()));
            newConfig.setCategory(targetVersionHistory.getCategory());
            newConfig.setDescription(targetVersionHistory.getDescription());
            newConfig.setEncrypted(targetVersionHistory.isEncrypted());
            newConfig.setConfigVersion(1);
            newConfig.setLastModifiedBy(currentUser);
            configRepository.save(newConfig);
        }

        // Create rollback history entry
        int newVersion = previousVersion + 1;
        ConfigVersionHistory rollbackEntry = new ConfigVersionHistory();
        rollbackEntry.setTenantId(tenantId);
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

        log.info("Config rolled back: tenant={}, key={}, from version {} to {}",
                tenantId, configKey, previousVersion, targetVersion);

        auditService.log("CONFIG_ROLLBACK", "SystemConfig", configKey,
                Map.of("tenantId", tenantId,
                        "fromVersion", previousVersion,
                        "toVersion", targetVersion,
                        "newVersion", newVersion));

        return configService.resolveConfig(configKey);
    }

    /**
     * Record a config change in version history.
     * Called by ConfigService when configs are created/updated.
     */
    @Transactional
    public void recordConfigChange(SystemConfig config, ConfigVersionHistory.ChangeType changeType) {
        String currentUser = TenantContext.getUserId();

        // Get next version number
        Integer maxVersion = versionHistoryRepository
                .findMaxVersionByTenantIdAndConfigKey(config.getTenantId(), config.getConfigKey());
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;

        // Update config version
        config.setConfigVersion(nextVersion);

        // Create history entry
        ConfigVersionHistory history = new ConfigVersionHistory();
        history.setTenantId(config.getTenantId());
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

        log.debug("Config version recorded: tenant={}, key={}, version={}, changeType={}",
                config.getTenantId(), config.getConfigKey(), nextVersion, changeType);
    }

    /**
     * Compare two versions of a config.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> compareVersions(String tenantId, String configKey, int version1, int version2) {
        ConfigVersionHistory v1 = getConfigVersion(tenantId, configKey, version1);
        ConfigVersionHistory v2 = getConfigVersion(tenantId, configKey, version2);

        return Map.of(
                "configKey", configKey,
                "version1", version1,
                "version2", version2,
                "value1", v1.getConfigValue(),
                "value2", v2.getConfigValue(),
                "areEqual", v1.getConfigValue().equals(v2.getConfigValue()),
                "changedAt1", v1.getChangedAt(),
                "changedAt2", v2.getChangedAt(),
                "changedBy1", v1.getChangedBy(),
                "changedBy2", v2.getChangedBy()
        );
    }
}
