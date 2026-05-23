package com.legent.content.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventContractValidator;
import com.legent.kafka.producer.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContentEventPublisherTest {

    @Mock
    private EventPublisher eventPublisher;

    private ContentEventPublisher publisher;
    private EventContractValidator contractValidator;

    @BeforeEach
    void setUp() {
        publisher = new ContentEventPublisher(eventPublisher);
        contractValidator = new EventContractValidator();
    }

    @Test
    void publishTemplatePublished_setsWorkspaceOnEnvelopeAndPayload() {
        publisher.publishTemplatePublished("tenant-1", "workspace-1", "template-1", "Template", "2");

        EventEnvelope<?> envelope = captureEnvelope();
        assertValidContentEvent(envelope);
        assertThat(envelope.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(envelope.getTenantId()).isEqualTo("tenant-1");
        assertThat(envelope.getPayload()).isInstanceOf(ContentPublishedEvent.class);

        ContentPublishedEvent payload = (ContentPublishedEvent) envelope.getPayload();
        assertThat(payload.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(payload.getTemplateId()).isEqualTo("template-1");
        assertThat(payload.getVersionNumber()).isEqualTo("2");
    }

    @Test
    void publishTemplateSubmittedForApproval_setsWorkspaceOnEnvelopeAndPayload() {
        publisher.publishTemplateSubmittedForApproval("tenant-1", "workspace-1", "template-1", "Template", 3);

        EventEnvelope<?> envelope = captureEnvelope();
        assertValidContentEvent(envelope);
        Map<String, Object> payload = payloadMap(envelope);

        assertThat(envelope.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(payload).containsEntry("workspaceId", "workspace-1")
                .containsEntry("templateId", "template-1")
                .containsEntry("versionNumber", 3);
    }

    @Test
    void publishTemplateApproved_setsWorkspaceOnEnvelopeAndPayload() {
        publisher.publishTemplateApproved("tenant-1", "workspace-1", "template-1", "Template", 4, "approver-1");

        EventEnvelope<?> envelope = captureEnvelope();
        assertValidContentEvent(envelope);
        Map<String, Object> payload = payloadMap(envelope);

        assertThat(envelope.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(payload).containsEntry("workspaceId", "workspace-1")
                .containsEntry("templateId", "template-1")
                .containsEntry("versionNumber", 4)
                .containsEntry("approvedBy", "approver-1");
    }

    @Test
    void publishTemplateRejected_setsWorkspaceOnEnvelopeAndPayload() {
        publisher.publishTemplateRejected("tenant-1", "workspace-1", "template-1", "Template", 5, "needs edits", "reviewer-1");

        EventEnvelope<?> envelope = captureEnvelope();
        assertValidContentEvent(envelope);
        Map<String, Object> payload = payloadMap(envelope);

        assertThat(envelope.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(payload).containsEntry("workspaceId", "workspace-1")
                .containsEntry("templateId", "template-1")
                .containsEntry("versionNumber", 5)
                .containsEntry("rejectionReason", "needs edits")
                .containsEntry("rejectedBy", "reviewer-1");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private EventEnvelope<?> captureEnvelope() {
        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_CONTENT_PUBLISHED), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadMap(EventEnvelope<?> envelope) {
        assertThat(envelope.getPayload()).isInstanceOf(Map.class);
        return (Map<String, Object>) envelope.getPayload();
    }

    private void assertValidContentEvent(EventEnvelope<?> envelope) {
        assertDoesNotThrow(() -> contractValidator.validate(AppConstants.TOPIC_CONTENT_PUBLISHED, envelope));
    }
}
