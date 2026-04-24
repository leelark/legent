package com.legent.delivery.service;

import com.legent.cache.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Circuit breaker for provider failover.
 * Tracks failure counts and opens circuit when threshold is exceeded.
 * Automatically closes circuit after recovery timeout.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderCircuitBreaker {

    private final CacheService cacheService;

    // Circuit breaker configuration
    private static final int FAILURE_THRESHOLD = 5;        // Open after 5 failures
    private static final Duration TIME_WINDOW = Duration.ofMinutes(5);  // Within 5 minutes
    private static final Duration OPEN_DURATION = Duration.ofMinutes(1); // Stay open for 1 minute
    private static final Duration HALF_OPEN_TIMEOUT = Duration.ofMinutes(2); // Try again after 2 minutes

    // Cache key prefixes
    private static final String FAILURE_COUNT_PREFIX = "cb:failures:";
    private static final String CIRCUIT_STATE_PREFIX = "cb:state:";
    private static final String LAST_FAILURE_PREFIX = "cb:lastfail:";

    public enum CircuitState {
        CLOSED,     // Normal operation
        OPEN,       // Circuit open, requests fail fast
        HALF_OPEN   // Testing if service recovered
    }

    /**
     * Records a success for the given provider.
     */
    public void recordSuccess(String providerId) {
        try {
            // Clear failure count on success
            cacheService.delete(FAILURE_COUNT_PREFIX + providerId);
            cacheService.delete(LAST_FAILURE_PREFIX + providerId);

            CircuitState currentState = getCircuitState(providerId);
            if (currentState == CircuitState.HALF_OPEN) {
                // Success in half-open state closes the circuit
                setCircuitState(providerId, CircuitState.CLOSED);
                log.info("Circuit breaker CLOSED for provider {} - recovery confirmed", providerId);
            }
        } catch (Exception e) {
            log.warn("Failed to record circuit breaker success: {}", e.getMessage());
        }
    }

    /**
     * Records a failure for the given provider.
     * Returns true if the circuit should be opened.
     */
    public boolean recordFailure(String providerId) {
        try {
            String failureKey = FAILURE_COUNT_PREFIX + providerId;
            String lastFailKey = LAST_FAILURE_PREFIX + providerId;

            // Get current failure count
            Optional<String> countOpt = cacheService.get(failureKey, String.class);
            int count = countOpt.map(Integer::parseInt).orElse(0);

            // Check if within time window
            Optional<String> lastFailOpt = cacheService.get(lastFailKey, String.class);
            if (lastFailOpt.isPresent()) {
                Instant lastFail = Instant.parse(lastFailOpt.get());
                if (Duration.between(lastFail, Instant.now()).compareTo(TIME_WINDOW) > 0) {
                    // Outside time window, reset count
                    count = 0;
                }
            }

            count++;
            cacheService.set(failureKey, String.valueOf(count), TIME_WINDOW);
            cacheService.set(lastFailKey, Instant.now().toString(), TIME_WINDOW);

            CircuitState currentState = getCircuitState(providerId);

            if (currentState == CircuitState.CLOSED && count >= FAILURE_THRESHOLD) {
                // Open the circuit
                setCircuitState(providerId, CircuitState.OPEN);
                log.warn("Circuit breaker OPENED for provider {} after {} failures", providerId, count);
                return true;
            }

            if (currentState == CircuitState.HALF_OPEN) {
                // Failure in half-open state reopens the circuit
                setCircuitState(providerId, CircuitState.OPEN);
                log.warn("Circuit breaker REOPENED for provider {} - recovery failed", providerId);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.warn("Failed to record circuit breaker failure: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks if the circuit is closed (allowing requests).
     */
    public boolean isCircuitClosed(String providerId) {
        CircuitState state = getCircuitState(providerId);

        if (state == CircuitState.CLOSED) {
            return true;
        }

        if (state == CircuitState.OPEN) {
            // Check if we should transition to half-open
            Optional<String> openedAtOpt = cacheService.get(CIRCUIT_STATE_PREFIX + providerId + ":opened", String.class);
            if (openedAtOpt.isPresent()) {
                Instant openedAt = Instant.parse(openedAtOpt.get());
                if (Duration.between(openedAt, Instant.now()).compareTo(HALF_OPEN_TIMEOUT) >= 0) {
                    // Transition to half-open
                    setCircuitState(providerId, CircuitState.HALF_OPEN);
                    log.info("Circuit breaker HALF_OPEN for provider {} - testing recovery", providerId);
                    return true; // Allow one test request
                }
            }
            return false; // Circuit still open
        }

        if (state == CircuitState.HALF_OPEN) {
            // Allow one request to test if service recovered
            return true;
        }

        return true; // Default to allowing requests
    }

    /**
     * Gets the current circuit state for a provider.
     */
    public CircuitState getCircuitState(String providerId) {
        try {
            Optional<String> stateOpt = cacheService.get(CIRCUIT_STATE_PREFIX + providerId, String.class);
            return stateOpt.map(CircuitState::valueOf).orElse(CircuitState.CLOSED);
        } catch (Exception e) {
            return CircuitState.CLOSED;
        }
    }

    /**
     * Manually resets the circuit to closed state.
     */
    public void resetCircuit(String providerId) {
        try {
            cacheService.delete(FAILURE_COUNT_PREFIX + providerId);
            cacheService.delete(CIRCUIT_STATE_PREFIX + providerId);
            cacheService.delete(CIRCUIT_STATE_PREFIX + providerId + ":opened");
            cacheService.delete(LAST_FAILURE_PREFIX + providerId);
            log.info("Circuit breaker manually reset for provider {}", providerId);
        } catch (Exception e) {
            log.warn("Failed to reset circuit breaker: {}", e.getMessage());
        }
    }

    private void setCircuitState(String providerId, CircuitState state) {
        try {
            cacheService.set(CIRCUIT_STATE_PREFIX + providerId, state.name(),
                    state == CircuitState.OPEN ? OPEN_DURATION : HALF_OPEN_TIMEOUT);

            if (state == CircuitState.OPEN) {
                cacheService.set(CIRCUIT_STATE_PREFIX + providerId + ":opened", Instant.now().toString(),
                        HALF_OPEN_TIMEOUT);
            }
        } catch (Exception e) {
            log.warn("Failed to set circuit state: {}", e.getMessage());
        }
    }
}
