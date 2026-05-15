package com.legent.delivery.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import java.util.Map;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.domain.ProviderDecisionTrace;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.domain.RoutingRule;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.repository.ProviderDecisionTraceRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import com.legent.delivery.repository.RoutingRuleRepository;
import com.legent.delivery.repository.SmtpProviderRepository;
import com.legent.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
public class ProviderSelectionStrategy {
    private static final Set<String> SMTP_COMPATIBLE_PROVIDER_TYPES = Set.of(
            "SMTP",
            "AWS_SES",
            "SENDGRID",
            "MAILGUN",
            "BREVO",
            "POSTMARK",
            "SPARKPOST",
            "POSTAL",
            "HARAKA",
            "POSTFIX",
            "MAILHOG",
            "MAILPIT",
            "DOCKER_MAIL_SERVER",
            "CUSTOM_SMTP"
    );

    private final RoutingRuleRepository routingRuleRepository;
    private final SmtpProviderRepository smtpProviderRepository;
    private final ProviderHealthStatusRepository providerHealthStatusRepository;
    private final ProviderCircuitBreaker circuitBreaker;
    private final Map<String, ProviderAdapter> adapters;
    private final String defaultProviderHost;
    private final int defaultProviderPort;
    private final boolean allowMockProviders;
    private final boolean allowDefaultProvider;

    @Autowired(required = false)
    private ProviderDecisionTraceRepository providerDecisionTraceRepository;

    public ProviderSelectionStrategy(
            RoutingRuleRepository routingRuleRepository,
            SmtpProviderRepository smtpProviderRepository,
            ProviderHealthStatusRepository providerHealthStatusRepository,
            ProviderCircuitBreaker circuitBreaker,
            List<ProviderAdapter> adapterList,
            @Value("${MAIL_HOST:mailhog}") String defaultProviderHost,
            @Value("${MAIL_PORT:1025}") int defaultProviderPort,
            @Value("${legent.delivery.allow-mock-provider:false}") boolean allowMockProviders,
            @Value("${legent.delivery.allow-default-provider:false}") boolean allowDefaultProvider) {
        this.routingRuleRepository = routingRuleRepository;
        this.smtpProviderRepository = smtpProviderRepository;
        this.providerHealthStatusRepository = providerHealthStatusRepository;
        this.circuitBreaker = circuitBreaker;
        this.defaultProviderHost = defaultProviderHost;
        this.defaultProviderPort = defaultProviderPort;
        this.allowMockProviders = allowMockProviders;
        this.allowDefaultProvider = allowDefaultProvider;
        this.adapters = adapterList.stream()
                .filter(adapter -> normalizeType(adapter.getProviderType()) != null)
                .collect(Collectors.toMap(
                        adapter -> normalizeType(adapter.getProviderType()),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    /**
     * Determines which Adapter and Provider DB Config to use for a given tenant and domain.
     */
    public ProviderSelectionResult selectProvider(String tenantId, String senderDomain) {
        String normalizedTenantId = normalizeIdentifier(tenantId);
        if (normalizedTenantId == null) {
            throw new IllegalArgumentException("Tenant id is required for provider selection");
        }

        String normalizedDomain = normalizeDomain(senderDomain);
        if (normalizedDomain == null) {
            throw new IllegalArgumentException("Sender domain is required for provider selection");
        }

        Optional<RoutingRule> ruleOpt = routingRuleRepository
                .findByTenantIdAndSenderDomainIgnoreCaseAndIsActiveTrue(normalizedTenantId, normalizedDomain);

        List<SmtpProvider> providers = smtpProviderRepository.findByTenantIdAndIsActiveTrueOrderByPriorityAsc(normalizedTenantId);
        if (providers.isEmpty() && allowDefaultProvider) {
            providers = List.of(createDefaultProvider(normalizedTenantId));
        }

        if (providers.isEmpty()) {
            throw new IllegalStateException("No active SMTP provider configured for tenant " + normalizedTenantId);
        }

        Map<String, ProviderHealthStatus> healthStatusByProvider = providerHealthStatusRepository.findByTenantId(normalizedTenantId)
                .stream()
                .collect(Collectors.toMap(ProviderHealthStatus::getProviderId, Function.identity(), (left, right) -> left));

        RoutingRule rule = ruleOpt.orElse(null);

        List<ScoredProvider> candidates = providers.stream()
                .filter(SmtpProvider::isActive)
                .filter(this::isProviderTypeAllowed)
                .filter(provider -> resolveAdapter(provider) != null)
                .filter(provider -> circuitBreaker.isCircuitClosed(provider.getId()))
                .map(provider -> scoreProvider(provider, healthStatusByProvider.get(provider.getId()), rule))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No healthy provider adapter available for tenant " + normalizedTenantId);
        }
        ScoredProvider selected = candidates.stream()
                .max(Comparator.comparingInt(ScoredProvider::score))
                .orElseThrow(() -> new IllegalStateException("No healthy provider adapter available for tenant " + normalizedTenantId));
        SmtpProvider selectedProvider = selected.provider();
        persistDecisionTrace(normalizedTenantId, normalizedDomain, candidates, selectedProvider);

        ProviderAdapter adapter = resolveAdapter(selectedProvider);
        if (adapter == null) {
            throw new IllegalStateException("No provider adapter registered for provider type " + selectedProvider.getType());
        }
        return new ProviderSelectionResult(adapter, selectedProvider);
    }

    public record ProviderSelectionResult(ProviderAdapter adapter, SmtpProvider dbRecord) {}

    /**
     * Records a successful provider invocation.
     */
    public void recordProviderSuccess(String providerId) {
        circuitBreaker.recordSuccess(providerId);
    }

    /**
     * Records a failed provider invocation.
     * Returns true if the circuit should be opened.
     */
    public boolean recordProviderFailure(String providerId) {
        return circuitBreaker.recordFailure(providerId);
    }

    private ProviderAdapter resolveAdapter(SmtpProvider providerConfig) {
        String providerType = normalizeType(providerConfig.getType());
        if (!allowMockProviders && "MOCK".equals(providerType)) {
            return null;
        }
        ProviderAdapter adapter = providerType != null ? adapters.get(providerType) : null;
        if (adapter != null) {
            return adapter;
        }
        if (providerType != null && SMTP_COMPATIBLE_PROVIDER_TYPES.contains(providerType)) {
            ProviderAdapter smtpAdapter = adapters.get("SMTP");
            if (smtpAdapter != null) {
                log.debug("Using SMTP adapter for SMTP-compatible provider type '{}'", providerType);
                return smtpAdapter;
            }
        }
        return null;
    }

    private boolean isProviderTypeAllowed(SmtpProvider provider) {
        String providerType = normalizeType(provider.getType());
        return allowMockProviders || !"MOCK".equals(providerType);
    }

    private ScoredProvider scoreProvider(SmtpProvider provider, ProviderHealthStatus healthStatus, RoutingRule rule) {
        int score = 0;
        StringBuilder factors = new StringBuilder();
        int priority = provider.getPriority() != null ? provider.getPriority() : 100;
        int priorityScore = Math.max(0, 25 - Math.min(priority, 25));
        score += priorityScore;
        factors.append("priority=").append(priorityScore);
        int maxSendRate = provider.getMaxSendRate() != null ? provider.getMaxSendRate() : 0;
        int rateScore = Math.min(20, Math.max(0, maxSendRate / 25));
        score += rateScore;
        factors.append(",rate=").append(rateScore);

        if (healthStatus != null) {
            int healthScore = healthStatus.getHealthScore() != null ? healthStatus.getHealthScore() : 60;
            score += healthScore;
            factors.append(",health=").append(healthScore);
            if (healthStatus.getCurrentStatus() == ProviderHealthStatus.HealthStatus.DEGRADED) {
                score -= 15;
                factors.append(",degraded=-15");
            } else if (healthStatus.getCurrentStatus() == ProviderHealthStatus.HealthStatus.UNHEALTHY) {
                score -= 40;
                factors.append(",unhealthy=-40");
            }
            if (healthStatus.isCircuitBreakerOpen()) {
                score -= 60;
                factors.append(",circuit=-60");
            }
        } else {
            score += 50;
            factors.append(",health=50");
        }

        int costPenalty = providerCostPenalty(normalizeType(provider.getType()));
        score -= costPenalty;
        factors.append(",cost=-").append(costPenalty);

        if (rule != null && rule.getProvider() != null && provider.getId().equals(rule.getProvider().getId())) {
            score += 25;
            factors.append(",domainAffinity=25");
        } else if (rule != null && rule.getFallbackProvider() != null && provider.getId().equals(rule.getFallbackProvider().getId())) {
            score += 10;
            factors.append(",fallbackAffinity=10");
        }
        return new ScoredProvider(provider, score, factors.toString());
    }

    private void persistDecisionTrace(String tenantId, String senderDomain, List<ScoredProvider> candidates, SmtpProvider selectedProvider) {
        if (providerDecisionTraceRepository == null) {
            return;
        }
        String workspaceId = TenantContext.getWorkspaceId();
        if (workspaceId == null || workspaceId.isBlank()) {
            return;
        }
        try {
            for (ScoredProvider candidate : candidates) {
                ProviderDecisionTrace trace = new ProviderDecisionTrace();
                trace.setTenantId(tenantId);
                trace.setWorkspaceId(workspaceId);
                trace.setProviderId(candidate.provider().getId());
                trace.setSenderDomain(senderDomain);
                trace.setRecipientDomain(null);
                trace.setScore(candidate.score());
                trace.setSelected(candidate.provider().getId().equals(selectedProvider.getId()));
                trace.setFactors(candidate.factors());
                providerDecisionTraceRepository.save(trace);
            }
        } catch (Exception e) {
            log.debug("Failed to persist provider decision trace: {}", e.getMessage());
        }
    }

    private int providerCostPenalty(String providerType) {
        if (providerType == null) {
            return 0;
        }
        return switch (providerType) {
            case "SMTP", "POSTAL", "HARAKA", "POSTFIX", "MAILHOG", "MAILPIT", "DOCKER_MAIL_SERVER", "CUSTOM_SMTP" -> 0;
            case "CUSTOM_API" -> 2;
            default -> 5;
        };
    }

    private String normalizeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeDomain(String value) {
        String normalized = normalizeIdentifier(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeType(String value) {
        String normalized = normalizeIdentifier(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private SmtpProvider createDefaultProvider(String tenantId) {
        SmtpProvider provider = new SmtpProvider();
        provider.setTenantId(tenantId);
        provider.setName("Default MailHog SMTP");
        provider.setType("SMTP");
        provider.setHost(defaultProviderHost);
        provider.setPort(defaultProviderPort);
        provider.setPriority(1);
        provider.setActive(true);
        provider.setHealthCheckEnabled(false);
        provider.setHealthStatus("HEALTHY");
        SmtpProvider saved = smtpProviderRepository.save(provider);
        log.info("Auto-created default provider {} for tenant {}", saved.getId(), tenantId);
        return saved;
    }

    private record ScoredProvider(SmtpProvider provider, int score, String factors) {}
}
