package com.legent.delivery.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.Socket;
import java.util.Locale;

import com.legent.common.security.OutboundUrlGuard;
import com.legent.delivery.domain.ProviderHealthCheck;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.repository.ProviderHealthCheckRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import com.legent.delivery.repository.SmtpProviderRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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

    static final int ACTIVE_PROVIDER_HEALTH_CHECK_PAGE_SIZE = 100;
    static final int CONSECUTIVE_FAILURE_HISTORY_LIMIT = 25;

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
        String lastProviderId = null;
        int checkedProviders = 0;

        while (true) {
            List<SmtpProvider> activeProviders = providerRepository.findActiveProvidersAfterId(
                    lastProviderId,
                    PageRequest.of(0, ACTIVE_PROVIDER_HEALTH_CHECK_PAGE_SIZE));
            if (activeProviders.isEmpty()) {
                break;
            }

            for (SmtpProvider provider : activeProviders) {
                lastProviderId = provider.getId();
                if (provider.isHealthCheckEnabled()) {
                    checkProviderHealth(provider);
                    checkedProviders++;
                }
            }

            if (activeProviders.size() < ACTIVE_PROVIDER_HEALTH_CHECK_PAGE_SIZE) {
                break;
            }
        }

        log.debug("Completed provider health checks for {} providers", checkedProviders);
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
        String workspaceId = resolveWorkspaceId(provider);
        Long totalSent = healthCheckRepository.countTotalChecks(provider.getTenantId(), workspaceId, provider.getId(), since24h);
        Long healthyChecks = healthCheckRepository.countHealthyChecks(provider.getTenantId(), workspaceId, provider.getId(), since24h);
        BigDecimal successRate = totalSent > 0 ? BigDecimal.valueOf(healthyChecks * 100.0 / totalSent) : BigDecimal.valueOf(100.0);

        // Record health check
        ProviderHealthCheck check = new ProviderHealthCheck();
        check.setTenantId(provider.getTenantId());
        check.setWorkspaceId(workspaceId);
        check.setProviderId(provider.getId());
        check.setStatus(status);
        check.setResponseTimeMs(responseTimeMs);
        check.setErrorMessage(errorMessage);
        check.setSuccessRate24h(successRate);
        check.setTotalSent24h(totalSent);
        check.setTotalFailed24h(totalSent - healthyChecks);

        // Count consecutive failures
        List<ProviderHealthCheck> recentChecks = healthCheckRepository
                .findByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDescIdDesc(
                        provider.getTenantId(),
                        workspaceId,
                        provider.getId(),
                        PageRequest.of(0, CONSECUTIVE_FAILURE_HISTORY_LIMIT));
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
            URI uri = OutboundUrlGuard.requirePublicHttpsUri(healthUrl, "provider health URL");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
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
        String workspaceId = resolveWorkspaceId(provider);
        Optional<ProviderHealthStatus> existingStatus = healthStatusRepository.findByTenantIdAndWorkspaceIdAndProviderId(
                provider.getTenantId(),
                workspaceId,
                provider.getId());

        ProviderHealthStatus status = existingStatus.orElseGet(() -> {
            ProviderHealthStatus newStatus = new ProviderHealthStatus();
            newStatus.setTenantId(provider.getTenantId());
            newStatus.setWorkspaceId(workspaceId);
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

    private String resolveWorkspaceId(SmtpProvider provider) {
        String workspaceId = provider.getWorkspaceId();
        if (workspaceId != null && !workspaceId.isBlank()) {
            return workspaceId;
        }
        throw new IllegalStateException("Workspace ownership is required for provider health monitoring");
    }

    /**
     * Get health status for all providers in a tenant.
     */
    public List<ProviderHealthStatus> getTenantProviderHealth(String tenantId) {
        return healthStatusRepository.findByTenantIdAndWorkspaceId(tenantId, TenantContext.requireWorkspaceId());
    }

    /**
     * Get unhealthy providers for a tenant.
     */
    public List<ProviderHealthStatus> getUnhealthyProviders(String tenantId) {
        String workspaceId = TenantContext.requireWorkspaceId();
        return healthStatusRepository.findUnhealthyProviders(tenantId, workspaceId);
    }

    /**
     * Check if a provider is healthy.
     */
    public boolean isProviderHealthy(String tenantId, String workspaceId, String providerId) {
        return healthStatusRepository.isProviderHealthy(tenantId, workspaceId, providerId);
    }

    /**
     * Get latest health check for a provider.
     */
    public Optional<ProviderHealthCheck> getLatestHealthCheck(String tenantId, String workspaceId, String providerId) {
        return healthCheckRepository.findFirstByTenantIdAndWorkspaceIdAndProviderIdOrderByCheckTimestampDesc(
                tenantId,
                workspaceId,
                providerId);
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
