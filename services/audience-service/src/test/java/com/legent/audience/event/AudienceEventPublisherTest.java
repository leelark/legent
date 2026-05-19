package com.legent.audience.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AudienceEventPublisherTest {

    @Mock
    private EventPublisher eventPublisher;

    private AudienceEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AudienceEventPublisher(eventPublisher);
        ReflectionTestUtils.setField(publisher, "doubleOptInConfirmationUrlTemplate",
                "https://app.example.test/confirm?tenant={tenantId}&subscriber={subscriberId}&token={token}");
        ReflectionTestUtils.setField(publisher, "doubleOptInFromEmail", "consent@example.test");
        ReflectionTestUtils.setField(publisher, "doubleOptInFromName", "Consent Desk");
        ReflectionTestUtils.setField(publisher, "doubleOptInReplyToEmail", "support@example.test");
        ReflectionTestUtils.setField(publisher, "doubleOptInSubject", "Confirm your subscription");
        ReflectionTestUtils.setField(publisher, "doubleOptInContentReference", "double-opt-in-confirmation");
        lenient().when(eventPublisher.publish(eq(AppConstants.TOPIC_EMAIL_SEND_REQUESTED), any()))
                .thenReturn(CompletableFuture.<SendResult<String, Object>>completedFuture(null));
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publishDoubleOptInRequestedBuildsDeliverySafePayloadWithoutRawTokenField() {
        publisher.publishDoubleOptInRequested("tenant-1", "subscriber-1", "user@example.test", "raw-token-123");

        ArgumentCaptor<EventEnvelope<Map<String, Object>>> captor = envelopeCaptor();
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_EMAIL_SEND_REQUESTED), captor.capture());

        EventEnvelope<Map<String, Object>> envelope = captor.getValue();
        Map<String, Object> payload = envelope.getPayload();
        assertThat(envelope.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(envelope.getIdempotencyKey()).asString().startsWith("doi-subscriber-1-");
        assertThat(payload)
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("subscriberId", "subscriber-1")
                .containsEntry("email", "user@example.test")
                .containsEntry("campaignId", "system-double-opt-in")
                .containsEntry("contentReference", "double-opt-in-confirmation")
                .containsEntry("subject", "Confirm your subscription")
                .containsEntry("fromEmail", "consent@example.test")
                .containsEntry("fromName", "Consent Desk")
                .containsEntry("replyToEmail", "support@example.test");
        assertThat(payload).doesNotContainKeys("token", "templateId", "eventType");
        assertThat(payload.get("htmlBody").toString())
                .contains("https://app.example.test/confirm")
                .contains("raw-token-123");
        assertThat(payload.get("messageId").toString()).startsWith("doi-subscriber-1-");
    }

    @Test
    void publishDoubleOptInRequestedFailsClosedWhenConfirmationTemplateIsMissingTokenPlaceholder() {
        ReflectionTestUtils.setField(publisher, "doubleOptInConfirmationUrlTemplate",
                "https://app.example.test/confirm");

        assertThatThrownBy(() -> publisher.publishDoubleOptInRequested(
                "tenant-1", "subscriber-1", "user@example.test", "raw-token-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{token}");
    }

    @Test
    void publishDoubleOptInRequestedFailsClosedWhenRequiredConfigIsMissing() {
        ReflectionTestUtils.setField(publisher, "doubleOptInFromEmail", " ");

        assertThatThrownBy(() -> publisher.publishDoubleOptInRequested(
                "tenant-1", "subscriber-1", "user@example.test", "raw-token-123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legent.consent.double-opt-in.from-email");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<EventEnvelope<Map<String, Object>>> envelopeCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(EventEnvelope.class);
    }
}
