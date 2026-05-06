package com.legent.delivery.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Locale;

import com.legent.delivery.domain.ProviderHealthCheck;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.repository.ProviderHealthCheckRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import com.legent.delivery.repository.SmtpProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for monitoring provider health and maintaining health status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderHealthMonitoringService {

    private final ProviderHealthCheckRepository healthCheckRepository;
    private final ProviderHealthStatusRepository healthStatusRepository;
    private final SmtpProviderRepository providerRepository;
    private final ProviderCircuitBreaker circuitBreaker;

    /**
     * Perform health checks on all active providers.
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    @Transactional
    public void performHealthChecks() {
        log.debug("Starting provider health checks");
        List<SmtpProvider> activeProviders = providerRepository.findAllActiveProviders();

        for (SmtpProvider provider : activeProviders) {
            if (provider.isHealthCheckEnabled()) {
                checkProviderHealth(provider);
            }
        }
    }

    /**
     * Check health of a specific provider.
     */
    @Transactional
    public void checkProviderHealth(SmtpProvider provider) {
        Instant startTime = Instant.now();
        ProviderHealthCheck.HealthStatus status;
        String errorMessage = null;
        Integer responseTimeMs = null;

        try {
            // Perform actual health check
            boolean isHealthy = performConnectionTest(provider);
            responseTimeMs = (int) Duration.between(startTime, Instant.now()).toMillis();

            if (isHealthy) {
                status = ProviderHealthCheck.HealthStatus.HEALTHY;
            } else {
                status = ProviderHealthCheck.HealthStatus.UNHEALTHY;
            }
        } catch (Exception e) {
            status = ProviderHealthCheck.HealthStatus.UNHEALTHY;
            errorMessage = e.getMessage();
            responseTimeMs = (int) Duration.between(startTime, Instant.now()).toMillis();
            log.warn("Health check failed for provider {}: {}", provider.getId(), e.getMessage());
        }

        // Calculate 24h metrics
        Instant since24h = Instant.now().minus(Duration.ofHours(24));
        Long totalSent = healthCheckRepository.countTotalChecks(provider.getId(), since24h);
        Long healthyChecks = healthCheckRepository.countHealthyChecks(provider.getId(), since24h);
        BigDecimal successRate = totalSent > 0 ? BigDecimal.valueOf(healthyChecks * 100.0 / totalSent) : BigDecimal.valueOf(100.0);

        // Record health check
        ProviderHealthCheck check = new ProviderHealthCheck();
        check.setTenantId(provider.getTenantId());
        check.setProviderId(provider.getId());
        check.setStatus(status);
        check.setResponseTimeMs(responseTimeMs);
        check.setErrorMessage(errorMessage);
        check.setSuccessRate24h(successRate);
        check.setTotalSent24h(totalSent);
        check.setTotalFailed24h(totalSent - healthyChecks);

        // Count consecutive failures
        List<ProviderHealthCheck> recentChecks = healthCheckRepository.findByProviderIdOrderByCheckTimestampDesc(provider.getId());
        int consecutiveFailures = 0;
        for (ProviderHealthCheck recentCheck : recentChecks) {
            if (recentCheck.getStatus() != ProviderHealthCheck.HealthStatus.HEALTHY) {
                consecutiveFailures++;
            } else {
                break;
            }
        }
        check.setConsecutiveFailures(consecutiveFailures);

        healthCheckRepository.save(check);

        // Update aggregated status
        updateHealthStatus(provider, status, consecutiveFailures);

        log.debug("Health check completed for provider {}: status={}, responseTime={}ms",
                provider.getId(), status, responseTimeMs);
    }

    /**
     * Perform actual connection test to provider.
     */
    private boolean performConnectionTest(SmtpProvider provider) {
        if (!provider.isActive()) {
            return false;
        }

        String healthUrl = normalize(provider.getHealthCheckUrl());
        if (healthUrl != null) {
            return checkHttpEndpoint(healthUrl);
        }

        String host = normalize(provider.getHost());
        Integer port = provider.getPort();
        if (host == null || port == null || port <= 0) {
            return false;
        }

        String providerType = normalize(provider.getType());
        if (providerType != null && providerType.contains("MOCK")) {
            return true;
        }

        return checkSocket(host, port);
    }

    private boolean checkHttpEndpoint(String healthUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(healthUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            log.debug("Provider health URL check failed for {}: {}", healthUrl, e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean checkSocket(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            return true;
        } catch (Exception e) {
            log.debug("Provider socket health check failed for {}:{}: {}", host, port, e.getMessage());
            return false;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized.toUpperCase(Locale.ROOT).equals("NULL") ? null : normalized;
    }

    /**
     * Update aggregated health status for a provider.
     */
    @Transactional
    public void updateHealthStatus(SmtpProvider provider, ProviderHealthCheck.HealthStatus checkStatus, int consecutiveFailures) {
        Optional<ProviderHealthStatus> existingStatus = healthStatusRepository.findByProviderId(provider.getId());

        ProviderHealthStatus status = existingStatus.orElseGet(() -> {
            ProviderHealthStatus newStatus = new ProviderHealthStatus();
            newStatus.setTenantId(provider.getTenantId());
            newStatus.setProviderId(provider.getId());
            return newStatus;
        });

        status.setLastCheckAt(Instant.now());
        status.setConsecutiveFailures(consecutiveFailures);
        status.setCircuitBreakerOpen(!circuitBreaker.isCircuitClosed(provider.getId()));

        // Determine overall status
        ProviderHealthStatus.HealthStatus currentStatus;
        if (consecutiveFailures >= 5 || status.isCircuitBreakerOpen()) {
            currentStatus = ProviderHealthStatus.HealthStatus.UNHEALTHY;
        } else if (consecutiveFailures >= 3) {
            currentStatus = ProviderHealthStatus.HealthStatus.DEGRADED;
        } else if (checkStatus == ProviderHealthCheck.HealthStatus.HEALTHY) {
            currentStatus = ProviderHealthStatus.HealthStatus.HEALTHY;
            status.recordSuccess();
        } else {
            currentStatus = ProviderHealthStatus.HealthStatus.DEGRADED;
            status.recordFailure();
        }

        status.setCurrentStatus(currentStatus);

        // Calculate health score (0-100)
        int score = 100;
        score -= consecutiveFailures * 10; // -10 per consecutive failure
        if (status.isCircuitBreakerOpen()) score -= 30; // -30 for circuit breaker
        status.setHealthScore(Math.max(0, score));

        healthStatusRepository.save(status);

        // Update provider record
        provider.setLastHealthCheckAt(Instant.now());
        provider.setHealthStatus(currentStatus.name());
        providerRepository.save(provider);
    }

    /**
     * Get health status for all providers in a tenant.
     */
    public List<ProviderHealthStatus> getTenantProviderHealth(String tenantId) {
        return healthStatusRepository.findByTenantId(tenantId);
    }

    /**
     * Get unhealthy providers for a tenant.
     */
    public List<ProviderHealthStatus> getUnhealthyProviders(String tenantId) {
        return healthStatusRepository.findUnhealthyProviders(tenantId);
    }

    /**
     * Check if a provider is healthy.
     */
    public boolean isProviderHealthy(String providerId) {
        return healthStatusRepository.isProviderHealthy(providerId);
    }

    /**
     * Get latest health check for a provider.
     */
    public Optional<ProviderHealthCheck> getLatestHealthCheck(String providerId) {
        return healthCheckRepository.findLatestCheck(providerId);
    }

    /**
     * Record a delivery success for provider health tracking.
     */
    @Transactional
    public void recordDeliverySuccess(String providerId) {
        circuitBreaker.recordSuccess(providerId);
    }

    /**
     * Record a delivery failure for provider health tracking.
     */
    @Transactional
    public void recordDeliveryFailure(String providerId) {
        boolean circuitOpen = circuitBreaker.recordFailure(providerId);
        if (circuitOpen) {
            log.warn("Circuit breaker opened for provider {}", providerId);
        }
    }
}
