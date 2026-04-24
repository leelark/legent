package com.legent.foundation.service;

import com.legent.common.constant.AppConstants;

import java.util.List;

import com.legent.cache.service.CacheService;
import com.legent.cache.service.TenantCacheKeyGenerator;
import com.legent.common.exception.NotFoundException;
import com.legent.foundation.domain.FeatureFlag;
import com.legent.foundation.dto.FeatureFlagDto;
import com.legent.foundation.mapper.FeatureFlagMapper;
import com.legent.foundation.repository.FeatureFlagRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * Service for managing feature flags with resolution hierarchy:
 * TENANT override → GLOBAL default
 * Cached in Redis with 1-minute TTL for fast evaluation.
 */
@Slf4j
@Service
@RequiredArgsConstructor

public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagMapper featureFlagMapper;
    private final CacheService cacheService;

    private static final Duration CACHE_TTL = Duration.ofSeconds(AppConstants.CACHE_FLAG_TTL_SECONDS);

    /**
     * Evaluates a feature flag for the current tenant.
     * Resolution order: Tenant-specific → Global.
     */
    @Transactional(readOnly = true)
    public FeatureFlagDto.EvaluationResult evaluate(String flagKey) {
        String tenantId = TenantContext.getTenantId();
        String cacheKey = TenantCacheKeyGenerator.key(AppConstants.CACHE_FEATURE_FLAG_PREFIX, flagKey);

        return cacheService.get(cacheKey, FeatureFlagDto.EvaluationResult.class)
                .orElseGet(() -> {
                    List<FeatureFlag> flags = featureFlagRepository
                            .findByKeyWithFallback(flagKey, tenantId);

                    if (flags.isEmpty()) {
                        // Flag not defined → disabled by default
                        return FeatureFlagDto.EvaluationResult.builder()
                                .flagKey(flagKey)
                                .enabled(false)
                                .resolvedScope("DEFAULT")
                                .build();
                    }

                    FeatureFlag resolved = flags.get(0);
                    String scope = (resolved.getTenantId() != null) ? "TENANT" : "GLOBAL";

                    FeatureFlagDto.EvaluationResult result = FeatureFlagDto.EvaluationResult.builder()
                            .flagKey(flagKey)
                            .enabled(resolved.isEnabled())
                            .resolvedScope(scope)
                            .build();

                    cacheService.set(cacheKey, result, CACHE_TTL);
                    return result;
                });
    }

    @Transactional(readOnly = true)
    public Page<FeatureFlagDto.Response> listFlags(String tenantId, Pageable pageable) {
        Page<FeatureFlag> flags = (tenantId != null)
                ? featureFlagRepository.findByTenantId(tenantId, pageable)
                : featureFlagRepository.findGlobalFlags(pageable);
        return flags.map(featureFlagMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public FeatureFlagDto.Response getFlag(String id) {
        FeatureFlag flag = featureFlagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FeatureFlag", id));
        return featureFlagMapper.toResponse(flag);
    }

    @Transactional
    public FeatureFlagDto.Response createFlag(String tenantId, FeatureFlagDto.CreateRequest request) {
        FeatureFlag entity = featureFlagMapper.toEntity(request);
        entity.setTenantId(tenantId);

        if (request.getScope() != null) {
            entity.setScope(FeatureFlag.FlagScope.valueOf(request.getScope()));
        }

        FeatureFlag saved = featureFlagRepository.save(entity);
        log.info("Feature flag created: key={}, tenantId={}", saved.getFlagKey(), tenantId);
        return featureFlagMapper.toResponse(saved);
    }

    @Transactional
    public FeatureFlagDto.Response updateFlag(String id, FeatureFlagDto.UpdateRequest request) {
        FeatureFlag existing = featureFlagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FeatureFlag", id));

        if (request.getEnabled() != null) {
            existing.setEnabled(request.getEnabled());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getRules() != null) {
            existing.setRules(request.getRules());
        }
        if (request.getMetadata() != null) {
            existing.setMetadata(request.getMetadata());
        }

        FeatureFlag saved = featureFlagRepository.save(existing);
        log.info("Feature flag updated: key={}, id={}", saved.getFlagKey(), id);

        // Invalidate cache using explicit tenantId from entity
        String cacheKey = generateTenantCacheKey(existing.getTenantId(), existing.getFlagKey());
        cacheService.delete(cacheKey);
        // Also evict global cache variant
        if (existing.getTenantId() != null) {
            String globalCacheKey = generateTenantCacheKey(null, existing.getFlagKey());
            cacheService.delete(globalCacheKey);
        }
        log.info("Cache invalidated for feature flag {}", existing.getFlagKey());

        return featureFlagMapper.toResponse(saved);
    }

    @Transactional
    public void deleteFlag(String id) {
        FeatureFlag existing = featureFlagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FeatureFlag", id));
        existing.softDelete();
        featureFlagRepository.save(existing);
        log.info("Feature flag soft-deleted: key={}, id={}", existing.getFlagKey(), id);

        // Invalidate cache for the deleted flag
        String cacheKey = generateTenantCacheKey(existing.getTenantId(), existing.getFlagKey());
        cacheService.delete(cacheKey);
        // Also evict global cache variant
        if (existing.getTenantId() != null) {
            String globalCacheKey = generateTenantCacheKey(null, existing.getFlagKey());
            cacheService.delete(globalCacheKey);
        }
        log.info("Cache invalidated for feature flag {}", existing.getFlagKey());
    }

    private String generateTenantCacheKey(String tenantId, String flagKey) {
        if (tenantId == null) {
            return AppConstants.CACHE_FEATURE_FLAG_PREFIX + "global:" + flagKey;
        }
        return AppConstants.CACHE_FEATURE_FLAG_PREFIX + tenantId + ":" + flagKey;
    }
}
