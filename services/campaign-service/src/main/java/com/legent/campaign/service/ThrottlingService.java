package com.legent.campaign.service;

import java.util.Optional;

import com.legent.common.constant.AppConstants;
import java.util.List;

import java.util.Map;

import com.legent.cache.service.CacheService;
import com.legent.campaign.domain.ThrottlingRule;
import com.legent.campaign.repository.ThrottlingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThrottlingService {

    private final ThrottlingRuleRepository ruleRepository;
    private final CacheService cacheService;

    // Local cache for rules to avoid DB hits on every check
    private final Map<String, List<ThrottlingRule>> rulesCache = new ConcurrentHashMap<>();

    public void reloadRules(String tenantId) {
        List<ThrottlingRule> rules = ruleRepository.findByTenantIdAndEnabledTrue(tenantId);
        rulesCache.put(tenantId, rules);
        log.info("Loaded {} throttling rules for tenant {}", rules.size(), tenantId);
    }

    private List<ThrottlingRule> getRules(String tenantId) {
        return rulesCache.computeIfAbsent(tenantId, k -> ruleRepository.findByTenantIdAndEnabledTrue(k));
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
        
        // Simple fixed-window rate limiting using Redis atomic increment
        // In a real prod environment with Redis, we would use a Lua script for sliding window or token bucket
        // Since we are using standard CacheService abstraction here:
        
        long currentCount = Optional.ofNullable(cacheService.get(counterKey, Long.class).orElse(0L)).orElse(0L);
        if (currentCount >= rule.getMaxEmails()) {
            return 0; // Throttled
        }

        long availablePermits = rule.getMaxEmails() - currentCount;
        int acquired = (int) Math.min(requestedPermits, availablePermits);
        
        // Simulate redis INCRBY and EXPIRE
        // For actual implementation, use StringRedisTemplate directly
        Long newCount = currentCount + acquired;
        cacheService.set(counterKey, newCount, Duration.ofSeconds(rule.getTimeWindowSeconds()));
        
        return acquired;
    }
}
