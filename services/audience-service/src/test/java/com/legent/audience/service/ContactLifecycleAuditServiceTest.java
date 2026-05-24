package com.legent.audience.service;

import java.util.List;
import java.util.Map;

import com.legent.audience.domain.ContactLifecycleAudit;
import com.legent.audience.domain.DataExtension;
import com.legent.audience.domain.Subscriber;
import com.legent.audience.domain.Suppression;
import com.legent.audience.repository.ContactLifecycleAuditRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContactLifecycleAuditServiceTest {

    @Mock
    private ContactLifecycleAuditRepository auditRepository;

    private ContactLifecycleAuditService service;

    @BeforeEach
    void setUp() {
        TenantContext.setUserId("user-1");
        service = new ContactLifecycleAuditService(auditRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void subscriberDeletedPersistsScopedAuditWithEmailHashOnly() {
        Subscriber subscriber = subscriber();

        service.subscriberDeleted(subscriber, "SUBSCRIBER_DELETE");

        ContactLifecycleAudit audit = capturedAudit();
        assertThat(audit.getTenantId()).isEqualTo("tenant-1");
        assertThat(audit.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(audit.getSubjectType()).isEqualTo("SUBSCRIBER");
        assertThat(audit.getSubscriberId()).isEqualTo("subscriber-1");
        assertThat(audit.getAction()).isEqualTo("SUBSCRIBER_DELETED");
        assertThat(audit.getEmailSha256()).hasSize(64);
        assertThat(audit.getMetadata()).doesNotContainKey("email");
        assertThat(audit.getPerformedBy()).isEqualTo("user-1");
    }

    @Test
    void suppressionDeletedPersistsScopedAudit() {
        Suppression suppression = suppression();

        service.suppressionDeleted(suppression, "SUPPRESSION_SERVICE");

        ContactLifecycleAudit audit = capturedAudit();
        assertThat(audit.getSubjectType()).isEqualTo("SUPPRESSION");
        assertThat(audit.getAction()).isEqualTo("SUPPRESSION_DELETED");
        assertThat(audit.getEmailSha256()).hasSize(64);
        assertThat(audit.getMetadata())
                .containsEntry("suppressionType", "UNSUBSCRIBE")
                .containsEntry("reasonPresent", true);
    }

    @Test
    void preferenceUpdatedScrubsUnsafeMetadataKeys() {
        service.preferenceUpdated(subscriber(), "PREFERENCE_UPDATED", Map.of(
                "email", "raw@example.com",
                "channelsChanged", true,
                "apiToken", "secret-value"));

        ContactLifecycleAudit audit = capturedAudit();
        assertThat(audit.getSubjectType()).isEqualTo("PREFERENCE");
        assertThat(audit.getAction()).isEqualTo("PREFERENCE_UPDATED");
        assertThat(audit.getMetadata()).containsEntry("channelsChanged", true);
        assertThat(audit.getMetadata()).doesNotContainKeys("email", "apiToken");
    }

    @Test
    void dataExtensionRetentionUpdatedPersistsScopedAudit() {
        DataExtension de = dataExtension();
        de.setRetentionAction("DELETE_RECORDS");
        de.setRetentionDays(90);
        de.setDataClassification("RESTRICTED");

        service.dataExtensionRetentionUpdated(de);

        ContactLifecycleAudit audit = capturedAudit();
        assertThat(audit.getSubjectType()).isEqualTo("DATA_EXTENSION");
        assertThat(audit.getDataExtensionId()).isEqualTo("de-1");
        assertThat(audit.getAction()).isEqualTo("DATA_EXTENSION_RETENTION_UPDATED");
        assertThat(audit.getMetadata())
                .containsEntry("retentionAction", "DELETE_RECORDS")
                .containsEntry("retentionDays", 90);
    }

    @Test
    void sendEligibilityCheckedPersistsAggregateOnly() {
        service.sendEligibilityChecked("tenant-1", "workspace-1", List.of(
                new SendEligibilityService.EligibilityResult("sub-1", "user@example.com", true, null),
                new SendEligibilityService.EligibilityResult("sub-2", "blocked@example.com", false, "SUPPRESSED")),
                2,
                0);

        ContactLifecycleAudit audit = capturedAudit();
        assertThat(audit.getSubjectType()).isEqualTo("SEND_ELIGIBILITY");
        assertThat(audit.getEmailSha256()).isNull();
        assertThat(audit.getMetadata())
                .containsEntry("requestedEmailCount", 2)
                .containsEntry("resultCount", 2)
                .containsEntry("eligibleCount", 1L)
                .containsEntry("ineligibleCount", 1L);
    }

    @Test
    void recordRejectsMissingTenantBeforeRepositoryAccess() {
        ContactLifecycleAuditService.AuditRequest request = ContactLifecycleAuditService.AuditRequest.builder()
                .workspaceId("workspace-1")
                .subjectType("SUBSCRIBER")
                .action("SUBSCRIBER_DELETED")
                .build();

        assertThatThrownBy(() -> service.record(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");

        verify(auditRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordRejectsMissingWorkspaceBeforeRepositoryAccess() {
        ContactLifecycleAuditService.AuditRequest request = ContactLifecycleAuditService.AuditRequest.builder()
                .tenantId("tenant-1")
                .subjectType("SUBSCRIBER")
                .action("SUBSCRIBER_DELETED")
                .build();

        assertThatThrownBy(() -> service.record(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workspaceId");

        verify(auditRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private ContactLifecycleAudit capturedAudit() {
        ArgumentCaptor<ContactLifecycleAudit> captor = ArgumentCaptor.forClass(ContactLifecycleAudit.class);
        verify(auditRepository).save(captor.capture());
        return captor.getValue();
    }

    private Subscriber subscriber() {
        Subscriber subscriber = new Subscriber();
        subscriber.setId("subscriber-1");
        subscriber.setTenantId("tenant-1");
        subscriber.setWorkspaceId("workspace-1");
        subscriber.setSubscriberKey("subscriber-key");
        subscriber.setEmail("User@Example.com");
        subscriber.setStatus(Subscriber.SubscriberStatus.ACTIVE);
        return subscriber;
    }

    private Suppression suppression() {
        Suppression suppression = new Suppression();
        suppression.setId("suppression-1");
        suppression.setTenantId("tenant-1");
        suppression.setWorkspaceId("workspace-1");
        suppression.setEmail("user@example.com");
        suppression.setSuppressionType(Suppression.SuppressionType.UNSUBSCRIBE);
        suppression.setReason("Opted out");
        return suppression;
    }

    private DataExtension dataExtension() {
        DataExtension dataExtension = new DataExtension();
        dataExtension.setId("de-1");
        dataExtension.setTenantId("tenant-1");
        dataExtension.setWorkspaceId("workspace-1");
        dataExtension.setName("Customers");
        return dataExtension;
    }
}
