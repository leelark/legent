package com.legent.foundation.service;

import com.legent.common.constant.AppConstants;
import java.util.List;

import java.util.Map;

import com.legent.cache.service.CacheService;
import com.legent.cache.service.TenantCacheKeyGenerator;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.SystemConfig;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.mapper.ConfigMapper;
import com.legent.foundation.repository.ConfigRepository;
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

/**
 * Service for managing system configurations.
 * Implements the config resolution hierarchy:
 * Tenant override → Global default
 * with Redis caching for sub-50ms reads.
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class ConfigService {

    private final ConfigRepository configRepository;
    private final ConfigMapper configMapper;
    private final CacheService cacheService;
    private final EventPublisher eventPublisher;

    private static final Duration CACHE_TTL = Duration.ofSeconds(AppConstants.CACHE_CONFIG_TTL_SECONDS);

    /**
     * Resolves a config value using the hierarchy:
     * 1. Tenant-specific override (if exists)
     * 2. Global default
     */
    @Transactional(readOnly = true)
    public ConfigDto.Response resolveConfig(String configKey) {
        String tenantId = TenantContext.getTenantId();
        String cacheKey = TenantCacheKeyGenerator.key(AppConstants.CACHE_CONFIG_PREFIX, configKey);

        // Check cache first
        return cacheService.get(cacheKey, ConfigDto.Response.class)
                .orElseGet(() -> {
                    List<SystemConfig> configs = configRepository
                            .findByKeyWithFallback(configKey, tenantId);
                    if (configs.isEmpty()) {
                        throw new NotFoundException("SystemConfig", configKey);
                    }
                    // First result is the highest-priority match
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
        if (configRepository.existsByTenantIdAndConfigKeyAndDeletedAtIsNull(tenantId, request.getConfigKey())) {
            throw new ConflictException("SystemConfig", "configKey", request.getConfigKey());
        }

        SystemConfig entity = configMapper.toEntity(request);
        entity.setTenantId(tenantId);

        if (request.getValueType() != null) {
            entity.setValueType(SystemConfig.ValueType.valueOf(request.getValueType()));
        }

        SystemConfig saved = configRepository.save(entity);
        log.info("Config created: key={}, tenantId={}", saved.getConfigKey(), tenantId);

        invalidateCache(tenantId, request.getConfigKey());
        publishConfigUpdatedEvent(tenantId, request.getConfigKey(), "CREATED");

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

        SystemConfig saved = configRepository.save(existing);
        log.info("Config updated: key={}, id={}", saved.getConfigKey(), id);

        invalidateCache(existing.getTenantId(), existing.getConfigKey());
        publishConfigUpdatedEvent(existing.getTenantId(), existing.getConfigKey(), "UPDATED");

        return configMapper.toResponse(saved);
    }

    @Transactional
    public void deleteConfig(String id) {
        SystemConfig existing = configRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SystemConfig", id));
        existing.softDelete();
        configRepository.save(existing);
        log.info("Config soft-deleted: key={}, id={}", existing.getConfigKey(), id);

        invalidateCache(existing.getTenantId(), existing.getConfigKey());
    }

    private void invalidateCache(String tenantId, String configKey) {
        String cacheKey = TenantCacheKeyGenerator.key(AppConstants.CACHE_CONFIG_PREFIX, configKey);
        cacheService.delete(cacheKey);
    }

    private void publishConfigUpdatedEvent(String tenantId, String configKey, String action) {
        EventEnvelope<Map<String, String>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_CONFIG_UPDATED,
                tenantId,
                "foundation-service",
                Map.of("configKey", configKey, "action", action));
        eventPublisher.publish(AppConstants.TOPIC_CONFIG_UPDATED, envelope);
    }
}
