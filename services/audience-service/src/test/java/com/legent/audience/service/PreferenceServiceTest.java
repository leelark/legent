package com.legent.audience.service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.domain.Suppression;
import com.legent.audience.dto.PreferenceDto;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.audience.repository.SuppressionRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenceServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";

    @Mock private SubscriberRepository subscriberRepository;
    @Mock private SuppressionRepository suppressionRepository;
    @Mock private ContactLifecycleAuditService lifecycleAuditService;

    private PreferenceService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
        service = new PreferenceService(subscriberRepository, suppressionRepository, lifecycleAuditService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void updateWritesPreferenceLifecycleAudit() {
        Subscriber subscriber = subscriber();
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndId(TENANT_ID, WORKSPACE_ID, "subscriber-1"))
                .thenReturn(Optional.of(subscriber));

        service.update("subscriber-1", PreferenceDto.UpdateRequest.builder()
                .channelPreferences(Map.of("email", false))
                .communicationFrequency("WEEKLY")
                .build());

        verify(subscriberRepository).save(subscriber);
        verify(lifecycleAuditService).preferenceUpdated(eq(subscriber), eq("PREFERENCE_UPDATED"), any());
    }

    @Test
    void pauseWritesPreferenceLifecycleAudit() {
        Subscriber subscriber = subscriber();
        Instant pausedUntil = Instant.now().plusSeconds(3600);
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndId(TENANT_ID, WORKSPACE_ID, "subscriber-1"))
                .thenReturn(Optional.of(subscriber));

        service.pause("subscriber-1", PreferenceDto.PauseRequest.builder()
                .pausedUntil(pausedUntil)
                .reason("Vacation")
                .build());

        assertThat(subscriber.getChannelPreferences()).containsEntry("pausedUntil", pausedUntil.toString());
        verify(lifecycleAuditService).preferenceUpdated(eq(subscriber), eq("PREFERENCE_PAUSED"), any());
    }

    @Test
    void unsubscribeCreatesSuppressionAndWritesPreferenceAndSuppressionAudit() {
        Subscriber subscriber = subscriber();
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndId(TENANT_ID, WORKSPACE_ID, "subscriber-1"))
                .thenReturn(Optional.of(subscriber));
        when(suppressionRepository.existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "user@example.com", Suppression.SuppressionType.UNSUBSCRIBE))
                .thenReturn(false);
        when(suppressionRepository.save(any(Suppression.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.unsubscribe("subscriber-1", PreferenceDto.UnsubscribeRequest.builder()
                .scope("GLOBAL")
                .reason("No longer interested")
                .build());

        assertThat(subscriber.getStatus()).isEqualTo(Subscriber.SubscriberStatus.UNSUBSCRIBED);
        assertThat(subscriber.getUnsubscribedAt()).isNotNull();
        verify(lifecycleAuditService).preferenceUpdated(eq(subscriber), eq("PREFERENCE_UNSUBSCRIBED"), any());
        verify(lifecycleAuditService).suppressionCreated(any(Suppression.class), eq("PREFERENCE_CENTER"));
    }

    @Test
    void unsubscribeExistingSuppressionDoesNotCreateDuplicateSuppressionAudit() {
        Subscriber subscriber = subscriber();
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndId(TENANT_ID, WORKSPACE_ID, "subscriber-1"))
                .thenReturn(Optional.of(subscriber));
        when(suppressionRepository.existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "user@example.com", Suppression.SuppressionType.UNSUBSCRIBE))
                .thenReturn(true);

        service.unsubscribe("subscriber-1", PreferenceDto.UnsubscribeRequest.builder()
                .scope("GLOBAL")
                .build());

        verify(suppressionRepository, never()).save(any(Suppression.class));
        verify(lifecycleAuditService).preferenceUpdated(eq(subscriber), eq("PREFERENCE_UNSUBSCRIBED"), any());
        verify(lifecycleAuditService, never()).suppressionCreated(any(), anyString());
    }

    @Test
    void unsubscribeRejectsForeignSubscriberWithoutSuppressionOrAudit() {
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndId(TENANT_ID, WORKSPACE_ID, "foreign-subscriber"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsubscribe("foreign-subscriber",
                PreferenceDto.UnsubscribeRequest.builder().scope("GLOBAL").build()))
                .isInstanceOf(NotFoundException.class);

        verify(suppressionRepository, never()).save(any());
        verify(lifecycleAuditService, never()).preferenceUpdated(any(), anyString(), any());
        verify(lifecycleAuditService, never()).suppressionCreated(any(), anyString());
    }

    @Test
    void resubscribeSoftDeletesUnsubscribeSuppressionAndWritesAudit() {
        Subscriber subscriber = subscriber();
        subscriber.setStatus(Subscriber.SubscriberStatus.UNSUBSCRIBED);
        Suppression suppression = unsubscribeSuppression();
        when(subscriberRepository.findByTenantIdAndWorkspaceIdAndId(TENANT_ID, WORKSPACE_ID, "subscriber-1"))
                .thenReturn(Optional.of(subscriber));
        when(suppressionRepository.findByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, "user@example.com", Suppression.SuppressionType.UNSUBSCRIBE))
                .thenReturn(Optional.of(suppression));

        service.resubscribe("subscriber-1");

        assertThat(subscriber.getStatus()).isEqualTo(Subscriber.SubscriberStatus.ACTIVE);
        assertThat(suppression.isDeleted()).isTrue();
        assertThat(suppression.getRecoveryStatus()).isEqualTo("RECOVERED");
        verify(lifecycleAuditService).preferenceUpdated(eq(subscriber), eq("PREFERENCE_RESUBSCRIBED"), any());
        verify(lifecycleAuditService).suppressionRecovered(suppression, "PREFERENCE_CENTER");
    }

    private Subscriber subscriber() {
        Subscriber subscriber = new Subscriber();
        subscriber.setId("subscriber-1");
        subscriber.setTenantId(TENANT_ID);
        subscriber.setWorkspaceId(WORKSPACE_ID);
        subscriber.setSubscriberKey("subscriber-key");
        subscriber.setEmail("user@example.com");
        subscriber.setStatus(Subscriber.SubscriberStatus.ACTIVE);
        return subscriber;
    }

    private Suppression unsubscribeSuppression() {
        Suppression suppression = new Suppression();
        suppression.setId("suppression-1");
        suppression.setTenantId(TENANT_ID);
        suppression.setWorkspaceId(WORKSPACE_ID);
        suppression.setEmail("user@example.com");
        suppression.setSuppressionType(Suppression.SuppressionType.UNSUBSCRIBE);
        return suppression;
    }
}
