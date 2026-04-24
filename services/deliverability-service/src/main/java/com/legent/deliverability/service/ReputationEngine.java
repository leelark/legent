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
    public void recordNegativeSignal(String tenantId, String domainId, String eventType) {
        String windowKey = "reputation:events:" + tenantId + ":" + domainId + ":" + eventType;
        long now = System.currentTimeMillis();

        // Add event to sliding window in Redis
        String luaScript = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local cutoff = now - (window * 1000)
            
            -- Add current event
            redis.call('ZADD', key, now, ARGV[3])
            -- Remove events outside the window
            redis.call('ZREMRANGEBYSCORE', key, 0, cutoff)
            -- Set expiry on the key
            redis.call('EXPIRE', key, window)
            
            -- Return count of events in window
            return redis.call('ZCARD', key)
            """;

        RedisScript<Long> redisScript = new DefaultRedisScript<>(luaScript, Long.class);
        String eventId = tenantId + ":" + now + ":" + java.util.UUID.randomUUID().toString();
        Long eventCount = cacheService.executeScript(redisScript,
                Arrays.asList(windowKey),
                String.valueOf(now),
                String.valueOf(WINDOW_SECONDS),
                eventId);

        int countInWindow = eventCount != null ? eventCount.intValue() : 1;

        // Calculate time-windowed rate (events per 1000 emails assumption)
        // For accurate calculation, you'd need total sent count from another counter
        double estimatedRate = Math.min(countInWindow / 1000.0, 1.0); // Cap at 100%

        // Update reputation with time-windowed calculation
        DomainReputation reputation = reputationRepository.findByDomainId(domainId)
                .orElseGet(() -> createNewReputation(tenantId, domainId));

        // Apply penalty based on time-windowed rate (not cumulative)
        double penalty = calculatePenalty(eventType, estimatedRate);
        int newScore = Math.max(MIN_REPUTATION,
                (int) (reputation.getReputationScore() - penalty));

        // Update rates using time-windowed values
        if ("HARD_BOUNCE".equals(eventType)) {
            reputation.setHardBounceRate(BigDecimal.valueOf(estimatedRate)
                    .setScale(4, RoundingMode.HALF_UP));
        } else if ("COMPLAINT".equals(eventType)) {
            reputation.setComplaintRate(BigDecimal.valueOf(estimatedRate)
                    .setScale(4, RoundingMode.HALF_UP));
        }

        reputation.setReputationScore(newScore);
        reputation.setCalculatedAt(Instant.now());
        reputationRepository.save(reputation);

        log.info("Domain {} reputation {} -> {} (window events: {}, rate: {:.4f}, type: {})",
                domainId, reputation.getReputationScore(), newScore, countInWindow, estimatedRate, eventType);
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
            String domainId = reputation.getDomainId();

            // Check if no negative events in the last recovery window
            String bounceKey = "reputation:events:" + tenantId + ":" + domainId + ":HARD_BOUNCE";
            String complaintKey = "reputation:events:" + tenantId + ":" + domainId + ":COMPLAINT";

            Long bounceCount = countEventsInLastHour(bounceKey, now);
            Long complaintCount = countEventsInLastHour(complaintKey, now);

            if ((bounceCount == null || bounceCount == 0) &&
                (complaintCount == null || complaintCount == 0)) {
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
            log.warn("Failed to count events for key {}", key, e);
            return null;
        }
    }

    private DomainReputation createNewReputation(String tenantId, String domainId) {
        DomainReputation dr = new DomainReputation();
        dr.setId(java.util.UUID.randomUUID().toString());
        dr.setTenantId(tenantId);
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
    public Optional<DomainReputation> getReputation(String domainId) {
        return reputationRepository.findByDomainId(domainId);
    }
}
