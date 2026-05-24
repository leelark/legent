package com.legent.audience.service;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.audience.repository.SuppressionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendEligibilityServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";

    @Mock private SubscriberRepository subscriberRepository;
    @Mock private SuppressionRepository suppressionRepository;
    @Mock private ContactLifecycleAuditService lifecycleAuditService;

    private SendEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new SendEligibilityService(subscriberRepository, suppressionRepository, lifecycleAuditService);
    }

    @Test
    void checkAuditsExplicitEligibilityRequestAsAggregate() {
        Subscriber subscriber = subscriber("subscriber-1", "user@example.com");
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "user@example.com")).thenReturn(java.util.Optional.of(subscriber));
        when(suppressionRepository.findActiveSuppression(TENANT_ID, WORKSPACE_ID, "user@example.com"))
                .thenReturn(List.of());

        List<SendEligibilityService.EligibilityResult> results = service.check(
                TENANT_ID, WORKSPACE_ID, List.of("user@example.com"), List.of());

        assertThat(results).singleElement().satisfies(result -> assertThat(result.eligible()).isTrue());
        verify(lifecycleAuditService).sendEligibilityChecked(eq(TENANT_ID), eq(WORKSPACE_ID), anyList(), eq(1), eq(0));
    }

    @Test
    void evaluateAllDeniesActiveLocalSuppressionWithBatchLookup() {
        Subscriber subscriber = subscriber("subscriber-1", " User@Example.COM ");
        when(suppressionRepository.findActiveSuppressedEmails(
                eq(TENANT_ID), eq(WORKSPACE_ID), eq(List.of("user@example.com"))))
                .thenReturn(List.of("USER@example.com"));

        List<SendEligibilityService.EligibilityResult> results = service.evaluateAll(
                TENANT_ID, WORKSPACE_ID, List.of(subscriber));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.subscriberId()).isEqualTo("subscriber-1");
            assertThat(result.email()).isEqualTo(" User@Example.COM ");
            assertThat(result.eligible()).isFalse();
            assertThat(result.reason()).isEqualTo("SUPPRESSED");
        });
    }

    @Test
    void evaluateAllDeniesNestedEmailChannelPreference() {
        Subscriber subscriber = subscriber("subscriber-1", "user@example.com");
        subscriber.setChannelPreferences(Map.of("channels", Map.of("email", false)));

        List<SendEligibilityService.EligibilityResult> results = service.evaluateAll(
                TENANT_ID, WORKSPACE_ID, List.of(subscriber));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.eligible()).isFalse();
            assertThat(result.reason()).isEqualTo("EMAIL_PREFERENCE_DISABLED");
        });
        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void evaluateAllDeniesFuturePauseUntilPreference() {
        Subscriber subscriber = subscriber("subscriber-1", "user@example.com");
        subscriber.setChannelPreferences(Map.of("pausedUntil", Instant.now().plusSeconds(3600).toString()));

        List<SendEligibilityService.EligibilityResult> results = service.evaluateAll(
                TENANT_ID, WORKSPACE_ID, List.of(subscriber));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.eligible()).isFalse();
            assertThat(result.reason()).isEqualTo("EMAIL_PREFERENCE_DISABLED");
        });
        verifyNoInteractions(suppressionRepository);
    }

    @Test
    void evaluateAllAllowsPastPauseWhenNoSuppressionExists() {
        Subscriber subscriber = subscriber("subscriber-1", "user@example.com");
        subscriber.setChannelPreferences(Map.of(
                "channels", Map.of("email", true),
                "pausedUntil", Instant.now().minusSeconds(60).toString()));
        when(suppressionRepository.findActiveSuppressedEmails(
                eq(TENANT_ID), eq(WORKSPACE_ID), eq(List.of("user@example.com"))))
                .thenReturn(List.of());

        List<SendEligibilityService.EligibilityResult> results = service.evaluateAll(
                TENANT_ID, WORKSPACE_ID, List.of(subscriber));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.eligible()).isTrue();
            assertThat(result.reason()).isNull();
        });
    }

    @Test
    void evaluateAllDeniesNullSubscriber() {
        List<SendEligibilityService.EligibilityResult> results = service.evaluateAll(
                TENANT_ID, WORKSPACE_ID, Collections.singletonList(null));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.subscriberId()).isNull();
            assertThat(result.email()).isNull();
            assertThat(result.eligible()).isFalse();
            assertThat(result.reason()).isEqualTo("SUBSCRIBER_NOT_FOUND");
        });
        verifyNoInteractions(suppressionRepository);
    }

    private Subscriber subscriber(String id, String email) {
        Subscriber subscriber = new Subscriber();
        subscriber.setId(id);
        subscriber.setTenantId(TENANT_ID);
        subscriber.setWorkspaceId(WORKSPACE_ID);
        subscriber.setSubscriberKey(id + "-key");
        subscriber.setEmail(email);
        subscriber.setStatus(Subscriber.SubscriberStatus.ACTIVE);
        return subscriber;
    }
}
