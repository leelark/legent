package com.legent.campaign.service;


import com.legent.common.constant.AppConstants;

import java.util.Map;

import com.legent.cache.service.CacheService;
import com.legent.campaign.domain.ThrottlingRule;
import com.legent.campaign.repository.ThrottlingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.Arrays;
import java.util.List;

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
