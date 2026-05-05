package com.legent.delivery.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import java.util.Map;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.domain.RoutingRule;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.repository.RoutingRuleRepository;
import com.legent.delivery.repository.SmtpProviderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
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
    private final ProviderCircuitBreaker circuitBreaker;
    private final Map<String, ProviderAdapter> adapters;
    private final String defaultProviderHost;
    private final int defaultProviderPort;

    public ProviderSelectionStrategy(
            RoutingRuleRepository routingRuleRepository,
            SmtpProviderRepository smtpProviderRepository,
            ProviderCircuitBreaker circuitBreaker,
            List<ProviderAdapter> adapterList,
            @Value("${MAIL_HOST:mailhog}") String defaultProviderHost,
            @Value("${MAIL_PORT:1025}") int defaultProviderPort) {
        this.routingRuleRepository = routingRuleRepository;
        this.smtpProviderRepository = smtpProviderRepository;
        this.circuitBreaker = circuitBreaker;
        this.defaultProviderHost = defaultProviderHost;
        this.defaultProviderPort = defaultProviderPort;
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

        SmtpProvider providerConfig = null;

        if (ruleOpt.isPresent() && ruleOpt.get().getProvider() != null) {
            providerConfig = ruleOpt.get().getProvider();
        } else {
            // Fall back to best priority default provider
            List<SmtpProvider> providers = smtpProviderRepository
                    .findByTenantIdAndIsActiveTrueOrderByPriorityAsc(normalizedTenantId);
            if (!providers.isEmpty()) {
                providerConfig = providers.get(0);
            } else {
                providerConfig = createDefaultProvider(normalizedTenantId);
            }
        }

        if (providerConfig == null || !providerConfig.isActive()) {
            throw new IllegalStateException("No active SMTP provider configured for tenant " + normalizedTenantId);
        }

        // Check circuit breaker before selecting provider
        String providerId = providerConfig.getId();
        if (!circuitBreaker.isCircuitClosed(providerId)) {
            log.warn("Circuit breaker OPEN for provider {}, attempting failover to fallback provider", providerId);
            providerConfig = selectFallbackProvider(normalizedTenantId, ruleOpt.orElse(null), providerConfig);
            if (providerConfig == null) {
                throw new IllegalStateException("Primary provider circuit open and no fallback available for tenant " + normalizedTenantId);
            }
            providerId = providerConfig.getId();
        }

        String providerType = normalizeType(providerConfig.getType());
        ProviderAdapter adapter = providerType != null ? adapters.get(providerType) : null;
        if (adapter != null) {
            return new ProviderSelectionResult(adapter, providerConfig);
        }

        if (providerType != null && SMTP_COMPATIBLE_PROVIDER_TYPES.contains(providerType)) {
            ProviderAdapter smtpAdapter = adapters.get("SMTP");
            if (smtpAdapter != null) {
                log.info("Using SMTP adapter for SMTP-compatible provider type '{}'", providerType);
                return new ProviderSelectionResult(smtpAdapter, providerConfig);
            }
        }

        ProviderAdapter fallbackAdapter = adapters.get("MOCK");
        if (fallbackAdapter != null) {
            log.warn("No adapter found for provider type '{}', falling back to MOCK adapter", providerConfig.getType());
            return new ProviderSelectionResult(fallbackAdapter, providerConfig);
        }

        throw new IllegalStateException("No provider adapter registered for provider type " + providerConfig.getType());
    }

    /**
     * Selects a fallback provider when the primary provider's circuit is open.
     */
    private SmtpProvider selectFallbackProvider(String tenantId, RoutingRule rule, SmtpProvider primaryProvider) {
        // First check if routing rule has a fallback provider configured
        if (rule != null && rule.getFallbackProvider() != null) {
            SmtpProvider fallback = rule.getFallbackProvider();
            if (fallback.isActive() && circuitBreaker.isCircuitClosed(fallback.getId())) {
                log.info("Using routing rule fallback provider: {}", fallback.getId());
                return fallback;
            }
        }

        // Otherwise, find next available provider by priority
        List<SmtpProvider> providers = smtpProviderRepository
                .findByTenantIdAndIsActiveTrueOrderByPriorityAsc(tenantId);

        for (SmtpProvider provider : providers) {
            // Skip the primary provider and any with open circuits
            if (!provider.getId().equals(primaryProvider.getId()) && circuitBreaker.isCircuitClosed(provider.getId())) {
                log.info("Using fallback provider {} for tenant {}", provider.getId(), tenantId);
                return provider;
            }
        }

        return null; // No fallback available
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
}
