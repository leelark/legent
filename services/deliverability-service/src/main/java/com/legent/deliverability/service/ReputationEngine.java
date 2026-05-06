package com.legent.deliverability.service;

import com.legent.cache.service.CacheService;
import com.legent.deliverability.domain.DomainReputation;
import com.legent.deliverability.repository.DomainReputationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Time-windowed reputation engine with recovery mechanism.
 * Uses 24-hour sliding windows in Redis for event counting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReputationEngine {

    private final DomainReputationRepository reputationRepository;
    private final CacheService cacheService;

    // Window size for rate calculation (24 hours in seconds)
    private static final long WINDOW_SECONDS = 24 * 60 * 60;
    // Recovery interval: reputation recovers slowly over time
    private static final long RECOVERY_WINDOW_MS = 60 * 60 * 1000; // 1 hour
    // Max penalty per event
    private static final double MAX_BOUNCE_PENALTY = 2.0;
    private static final double MAX_COMPLAINT_PENALTY = 5.0;
    // Recovery rate: points recovered per hour without negative events
    private static final double RECOVERY_RATE = 1.0;
    // Maximum reputation score
    private static final int MAX_REPUTATION = 100;
    // Minimum reputation score
    private static final int MIN_REPUTATION = 0;

    /**
     * Records a negative signal (bounce or complaint) with time-windowed calculation.
     * Uses Redis sorted sets for sliding window event tracking.
     */
    @Transactional
    public void recordNegativeSignal(String tenantId, String workspaceId, String domainId, String eventType) {
        String normalizedWorkspaceId = requireWorkspace(workspaceId);
        String windowKey = "reputation:events:" + tenantId + ":" + normalizedWorkspaceId + ":" + domainId + ":" + eventType;
        long now = System.currentTimeMillis();

        // Add event to sliding window in Redis
        String luaScript = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2] or "86400")
            local eventId = ARGV[3] or tostring(now)
            local cutoff = now - (window * 1000)
            
            -- Add current event
            redis.call('ZADD', key, now, eventId)
            -- Remove events outside the window
            redis.call('ZREMRANGEBYSCORE', key, 0, cutoff)
            -- Set expiry on the key
            redis.call('EXPIRE', key, window)
            
            -- Return count of events in window
            return redis.call('ZCARD', key)
            """;

        RedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript, Long.class);
        String eventId = tenantId + ":" + normalizedWorkspaceId + ":" + now + ":" + java.util.UUID.randomUUID();
        Long eventCount;
        try {
            eventCount = cacheService.executeScript(redisScript,
                    Arrays.asList(windowKey),
                    String.valueOf(now),
                    String.valueOf(WINDOW_SECONDS),
                    eventId);
        } catch (Exception e) {
            // Feedback ingestion must stay resilient even if Redis window script fails.
            log.warn("Sliding-window cache update failed for domain {} eventType {}: {}", domainId, eventType, e.getMessage());
            eventCount = 1L;
        }

        int countInWindow = eventCount != null ? eventCount.intValue() : 0;
        if (eventCount == null) {
            log.warn("Failed to get event count from cache for domain {}. Using 0.", domainId);
        }

        // AUDIT-017: Get actual total sent count from cache for accurate rate calculation
        String sentKey = "reputation:sent:" + tenantId + ":" + normalizedWorkspaceId + ":" + domainId;
        Long totalSent = getTotalSentFromCache(sentKey);
        
        // Calculate actual rate using real denominator
        double actualRate;
        if (totalSent != null && totalSent > 0) {
            actualRate = Math.min(countInWindow / (double) totalSent, 1.0);
        } else {
            // Fallback: assume 1000 as baseline if no sent count available
            actualRate = Math.min(countInWindow / 1000.0, 1.0);
            log.debug("No total sent count available for domain {}, using baseline assumption", domainId);
        }

        // Update reputation with time-windowed calculation
        DomainReputation reputation = reputationRepository.findByTenantIdAndWorkspaceIdAndDomainId(tenantId, normalizedWorkspaceId, domainId)
                .orElseGet(() -> createNewReputation(tenantId, normalizedWorkspaceId, domainId));

        // Apply penalty based on time-windowed rate (not cumulative)
        double penalty = calculatePenalty(eventType, actualRate);
        int newScore = Math.max(MIN_REPUTATION,
                (int) (reputation.getReputationScore() - penalty));

        // Update rates using time-windowed values
        if ("HARD_BOUNCE".equals(eventType)) {
            reputation.setHardBounceRate(BigDecimal.valueOf(actualRate)
                    .setScale(4, RoundingMode.HALF_UP));
        } else if ("COMPLAINT".equals(eventType)) {
            reputation.setComplaintRate(BigDecimal.valueOf(actualRate)
                    .setScale(4, RoundingMode.HALF_UP));
        }

        reputation.setReputationScore(newScore);
        reputation.setCalculatedAt(Instant.now());
        reputationRepository.save(reputation);

        log.info("Domain {} reputation {} -> {} (window events: {}, rate: {:.4f}, type: {})",
                domainId, reputation.getReputationScore(), newScore, countInWindow, actualRate, eventType);
    }

    /**
     * Scheduled recovery mechanism - slowly restores reputation over time.
     * Runs every hour to give reputation points back to domains without negative events.
     */
    @Scheduled(fixedDelay = RECOVERY_WINDOW_MS)
    @Transactional
    public void applyReputationRecovery() {
        log.debug("Running reputation recovery job");
        List<DomainReputation> allReputations = reputationRepository.findAll();
        long now = System.currentTimeMillis();

        for (DomainReputation reputation : allReputations) {
            String tenantId = reputation.getTenantId();
            String workspaceId = reputation.getWorkspaceId();
            if (workspaceId == null || workspaceId.isBlank()) {
                log.warn("Skipping reputation recovery for domain {} due to missing workspace context", reputation.getDomainId());
                continue;
            }
            workspaceId = workspaceId.trim();
            String domainId = reputation.getDomainId();

            // Check if no negative events in the last recovery window
            String bounceKey = "reputation:events:" + tenantId + ":" + workspaceId + ":" + domainId + ":HARD_BOUNCE";
            String complaintKey = "reputation:events:" + tenantId + ":" + workspaceId + ":" + domainId + ":COMPLAINT";

            Long bounceCount = countEventsInLastHour(bounceKey, now);
            Long complaintCount = countEventsInLastHour(complaintKey, now);

            // AUDIT-017: Skip recovery when we can't read from Redis (fail closed)
            if (bounceCount == null || complaintCount == null) {
                log.warn("Redis unavailable, skipping reputation recovery for domain {}. Scores will not change.", domainId);
                continue;
            }

            if (bounceCount == 0 && complaintCount == 0) {
                // No negative events in last hour, apply recovery
                int currentScore = reputation.getReputationScore();
                if (currentScore < MAX_REPUTATION) {
                    int newScore = Math.min(MAX_REPUTATION,
                            (int) (currentScore + RECOVERY_RATE));
                    reputation.setReputationScore(newScore);
                    reputation.setCalculatedAt(Instant.now());
                    reputationRepository.save(reputation);
                    log.debug("Domain {} reputation recovered: {} -> {}",
                            domainId, currentScore, newScore);
                }
            }
        }
    }

    private Long countEventsInLastHour(String key, long now) {
        try {
            String luaScript = """
                local key = KEYS[1]
                local oneHourAgo = tonumber(ARGV[1]) - 3600000
                return redis.call('ZCOUNT', key, oneHourAgo, '+inf')
                """;
            RedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript, Long.class);
            return cacheService.executeScript(redisScript,
                    Arrays.asList(key), String.valueOf(now));
        } catch (Exception e) {
            log.error("Failed to count events for key {}: {}. Cache unavailable - cannot verify event count.", key, e.getMessage());
            // AUDIT-017: Return null on cache failure to distinguish from "no events"
            return null;
        }
    }
    
    /**
     * AUDIT-017: Get total sent count from cache for accurate rate calculation.
     */
    private Long getTotalSentFromCache(String key) {
        try {
            Optional<Long> count = cacheService.get(key, Long.class);
            return count.orElse(null);
        } catch (Exception e) {
            log.warn("Failed to get total sent count from cache: {}", e.getMessage());
            return null;
        }
    }

    private DomainReputation createNewReputation(String tenantId, String workspaceId, String domainId) {
        DomainReputation dr = new DomainReputation();
        dr.setId(java.util.UUID.randomUUID().toString());
        dr.setTenantId(tenantId);
        dr.setWorkspaceId(requireWorkspace(workspaceId));
        dr.setOwnershipScope("WORKSPACE");
        dr.setDomainId(domainId);
        dr.setReputationScore(100); // Start with perfect reputation
        dr.setHardBounceRate(BigDecimal.ZERO);
        dr.setComplaintRate(BigDecimal.ZERO);
        return dr;
    }

    private double calculatePenalty(String eventType, double rate) {
        // Penalty scales with rate, but is capped
        if ("HARD_BOUNCE".equals(eventType)) {
            return Math.min(MAX_BOUNCE_PENALTY, MAX_BOUNCE_PENALTY * rate);
        } else if ("COMPLAINT".equals(eventType)) {
            return Math.min(MAX_COMPLAINT_PENALTY, MAX_COMPLAINT_PENALTY * rate * 2);
        }
        return 1.0;
    }

    /**
     * Gets current reputation for a domain, calculating from time window if needed.
     */
    public Optional<DomainReputation> getReputation(String tenantId, String workspaceId, String domainId) {
        return reputationRepository.findByTenantIdAndWorkspaceIdAndDomainId(tenantId, requireWorkspace(workspaceId), domainId);
    }

    private String requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required for deliverability reputation operations");
        }
        return workspaceId.trim();
    }
}
