package com.legent.campaign.service;


import com.legent.common.constant.AppConstants;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.cache.service.CacheService;
import com.legent.campaign.domain.ThrottlingRule;
import com.legent.campaign.repository.ThrottlingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThrottlingService {

    private final ThrottlingRuleRepository ruleRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    // Redis key prefix for throttling rules cache
    private static final String RULES_CACHE_PREFIX = "throttle:rules:";
    private static final Duration RULES_CACHE_TTL = Duration.ofMinutes(5);

    /**
     * Reloads throttling rules from DB into Redis cache.
     * Called when rules are updated or periodically.
     */
    public void reloadRules(String tenantId) {
        List<ThrottlingRule> rules = ruleRepository.findByTenantIdAndEnabledTrue(tenantId);
        String cacheKey = RULES_CACHE_PREFIX + tenantId;
        try {
            String rulesJson = objectMapper.writeValueAsString(rules);
            cacheService.set(cacheKey, rulesJson, RULES_CACHE_TTL);
            log.info("Loaded {} throttling rules for tenant {} into Redis cache", rules.size(), tenantId);
        } catch (Exception e) {
            log.error("Failed to cache throttling rules for tenant {}", tenantId, e);
        }
    }

    /**
     * Gets throttling rules from Redis cache, loading from DB if not present.
     */
    private List<ThrottlingRule> getRules(String tenantId) {
        String cacheKey = RULES_CACHE_PREFIX + tenantId;
        try {
            java.util.Optional<String> cachedRulesOpt = cacheService.get(cacheKey, String.class);
            if (cachedRulesOpt.isPresent()) {
                return objectMapper.readValue(cachedRulesOpt.get(), new TypeReference<List<ThrottlingRule>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to deserialize throttling rules from cache for tenant {}", tenantId, e);
        }
        // Fallback to DB and cache
        List<ThrottlingRule> rules = ruleRepository.findByTenantIdAndEnabledTrue(tenantId);
        try {
            String rulesJson = objectMapper.writeValueAsString(rules);
            cacheService.set(cacheKey, rulesJson, RULES_CACHE_TTL);
        } catch (Exception e) {
            log.error("Failed to cache throttling rules for tenant {}", tenantId, e);
        }
        return rules;
    }

    /**
     * Invalidates the rules cache for a tenant.
     * Called when rules are modified.
     */
    public void invalidateRulesCache(String tenantId) {
        String cacheKey = RULES_CACHE_PREFIX + tenantId;
        cacheService.delete(cacheKey);
        log.info("Invalidated throttling rules cache for tenant {}", tenantId);
    }

    /**
     * Attempts to acquire permits for sending to a specific domain.
     * Uses Redis for distributed rate limiting.
     * Returns the number of permits successfully acquired.
     */
    public int acquirePermits(String tenantId, String domain, int requestedPermits) {
        List<ThrottlingRule> rules = getRules(tenantId);
        var ruleOpt = rules.stream().filter(r -> r.getDomain().equalsIgnoreCase(domain)).findFirst();
        
        if (ruleOpt.isEmpty()) {
            ruleOpt = rules.stream().filter(r -> r.getDomain().equals("*")).findFirst(); // default rule
        }

        if (ruleOpt.isEmpty()) {
            return requestedPermits; // No throttling rule for this domain
        }

        ThrottlingRule rule = ruleOpt.get();
        String counterKey = AppConstants.CACHE_THROTTLE_DOMAIN_PREFIX + tenantId + ":" + domain;
        
        // Lua script for sliding window rate limiting
        // ARGS: [limit, windowSeconds, requestedAmount]
        String script = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local amount = tonumber(ARGV[3])
            
            local current = redis.call('GET', key)
            if current and tonumber(current) >= limit then
                return 0
            end
            
            local available = limit - (current and tonumber(current) or 0)
            local acquired = math.min(amount, available)
            
            if current then
                redis.call('INCRBY', key, acquired)
            else
                redis.call('SET', key, acquired)
                redis.call('EXPIRE', key, window)
            end
            
            return acquired
        """;

        RedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long acquired = cacheService.executeScript(redisScript, 
                Arrays.asList(counterKey), 
                String.valueOf(rule.getMaxEmails()), 
                String.valueOf(rule.getTimeWindowSeconds()), 
                String.valueOf(requestedPermits));

        return acquired != null ? acquired.intValue() : 0;
    }
}
