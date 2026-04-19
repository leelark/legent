package com.legent.delivery.service;

import java.util.List;
import java.util.Optional;

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

@Slf4j
@Service
public class ProviderSelectionStrategy {

    private final RoutingRuleRepository routingRuleRepository;
    private final SmtpProviderRepository smtpProviderRepository;
    private final Map<String, ProviderAdapter> adapters;

    public ProviderSelectionStrategy(
            RoutingRuleRepository routingRuleRepository,
            SmtpProviderRepository smtpProviderRepository,
            List<ProviderAdapter> adapterList) {
        this.routingRuleRepository = routingRuleRepository;
        this.smtpProviderRepository = smtpProviderRepository;
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
            }
        }

        if (providerConfig == null || !providerConfig.isActive()) {
            throw new IllegalStateException("No active SMTP provider configured for tenant " + normalizedTenantId);
        }

        String providerType = normalizeType(providerConfig.getType());
        ProviderAdapter adapter = providerType != null ? adapters.get(providerType) : null;
        if (adapter != null) {
            return new ProviderSelectionResult(adapter, providerConfig);
        }

        ProviderAdapter fallbackAdapter = adapters.get("MOCK");
        if (fallbackAdapter != null) {
            log.warn("No adapter found for provider type '{}', falling back to MOCK adapter", providerConfig.getType());
            return new ProviderSelectionResult(fallbackAdapter, providerConfig);
        }

        throw new IllegalStateException("No provider adapter registered for provider type " + providerConfig.getType());
    }

    public record ProviderSelectionResult(ProviderAdapter adapter, SmtpProvider dbRecord) {}

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
}
