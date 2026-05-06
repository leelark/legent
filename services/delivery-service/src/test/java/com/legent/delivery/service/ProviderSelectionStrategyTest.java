package com.legent.delivery.service;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.domain.RoutingRule;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import com.legent.delivery.repository.RoutingRuleRepository;
import com.legent.delivery.repository.SmtpProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderSelectionStrategyTest {

    @Mock private RoutingRuleRepository routingRuleRepository;
    @Mock private SmtpProviderRepository smtpProviderRepository;
    @Mock private ProviderHealthStatusRepository providerHealthStatusRepository;
    @Mock private ProviderCircuitBreaker circuitBreaker;
    @Mock private ProviderAdapter smtpAdapter;

    private ProviderSelectionStrategy strategy;

    @BeforeEach
    void setUp() {
        when(smtpAdapter.getProviderType()).thenReturn("SMTP");
        lenient().when(circuitBreaker.isCircuitClosed("provider-1")).thenReturn(true);
        when(providerHealthStatusRepository.findByTenantId("tenant-1")).thenReturn(List.of());
        strategy = new ProviderSelectionStrategy(
                routingRuleRepository,
                smtpProviderRepository,
                providerHealthStatusRepository,
                circuitBreaker,
                List.of(smtpAdapter),
                "mailhog",
                1025,
                false
        );
    }

    @Test
    void selectProvider_usesRoutingRuleProviderWithCaseInsensitiveType() {
        SmtpProvider provider = activeProvider("smtp");
        RoutingRule rule = new RoutingRule();
        rule.setProvider(provider);

        when(routingRuleRepository.findByTenantIdAndSenderDomainIgnoreCaseAndIsActiveTrue("tenant-1", "example.com"))
                .thenReturn(Optional.of(rule));
        when(smtpProviderRepository.findByTenantIdAndIsActiveTrueOrderByPriorityAsc("tenant-1"))
                .thenReturn(List.of(provider));

        ProviderSelectionStrategy.ProviderSelectionResult result = strategy.selectProvider(" tenant-1 ", "EXAMPLE.COM");

        assertSame(smtpAdapter, result.adapter());
        assertSame(provider, result.dbRecord());
    }

    @Test
    void selectProvider_whenNoRule_usesPriorityProvider() {
        SmtpProvider provider = activeProvider("SMTP");

        when(routingRuleRepository.findByTenantIdAndSenderDomainIgnoreCaseAndIsActiveTrue("tenant-1", "example.com"))
                .thenReturn(Optional.empty());
        when(smtpProviderRepository.findByTenantIdAndIsActiveTrueOrderByPriorityAsc("tenant-1"))
                .thenReturn(List.of(provider));

        ProviderSelectionStrategy.ProviderSelectionResult result = strategy.selectProvider("tenant-1", "example.com");

        assertSame(smtpAdapter, result.adapter());
        assertSame(provider, result.dbRecord());
    }

    @Test
    void selectProvider_whenSmtpCompatibleType_usesSmtpAdapter() {
        SmtpProvider provider = activeProvider("AWS_SES");
        RoutingRule rule = new RoutingRule();
        rule.setProvider(provider);

        when(routingRuleRepository.findByTenantIdAndSenderDomainIgnoreCaseAndIsActiveTrue("tenant-1", "example.com"))
                .thenReturn(Optional.of(rule));
        when(smtpProviderRepository.findByTenantIdAndIsActiveTrueOrderByPriorityAsc("tenant-1"))
                .thenReturn(List.of(provider));

        ProviderSelectionStrategy.ProviderSelectionResult result = strategy.selectProvider("tenant-1", "example.com");

        assertSame(smtpAdapter, result.adapter());
        assertSame(provider, result.dbRecord());
    }

    @Test
    void selectProvider_whenNoMatchingAdapterAndNoFallback_throws() {
        SmtpProvider provider = activeProvider("UNKNOWN_VENDOR");
        RoutingRule rule = new RoutingRule();
        rule.setProvider(provider);

        when(routingRuleRepository.findByTenantIdAndSenderDomainIgnoreCaseAndIsActiveTrue("tenant-1", "example.com"))
                .thenReturn(Optional.of(rule));
        when(smtpProviderRepository.findByTenantIdAndIsActiveTrueOrderByPriorityAsc("tenant-1"))
                .thenReturn(List.of(provider));

        ProviderSelectionStrategy noFallbackStrategy = new ProviderSelectionStrategy(
                routingRuleRepository,
                smtpProviderRepository,
                providerHealthStatusRepository,
                circuitBreaker,
                List.of(smtpAdapter),
                "mailhog",
                1025,
                false
        );

        assertThrows(IllegalStateException.class, () -> noFallbackStrategy.selectProvider("tenant-1", "example.com"));
    }

    private SmtpProvider activeProvider(String type) {
        SmtpProvider provider = new SmtpProvider();
        provider.setId("provider-1");
        provider.setType(type);
        provider.setActive(true);
        return provider;
    }
}
