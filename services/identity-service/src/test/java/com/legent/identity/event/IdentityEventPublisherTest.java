package com.legent.identity.event;

import com.legent.common.constant.AppConstants;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IdentityEventPublisherTest {

    @Test
    void publishPasswordResetEmail_escapesResetUrlInHtmlBody() {
        EventPublisher eventPublisher = mock(EventPublisher.class);
        IdentityEventPublisher publisher = new IdentityEventPublisher(eventPublisher);
        String resetUrl = "https://app.legent.test/reset-password?token=abc\" onclick=\"alert(1)&next=<x>";

        publisher.publishPasswordResetEmail(
                "tenant-1",
                "workspace-1",
                "user-1",
                "user@example.com",
                resetUrl,
                "reset-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<EventEnvelope<Map<String, Object>>> envelopeCaptor =
                ArgumentCaptor.forClass(EventEnvelope.class);
        verify(eventPublisher).publish(eq(AppConstants.TOPIC_EMAIL_SEND_REQUESTED), envelopeCaptor.capture());
        String htmlBody = String.valueOf(envelopeCaptor.getValue().getPayload().get("htmlBody"));

        assertThat(htmlBody).doesNotContain("onclick=\"alert(1)");
        assertThat(htmlBody).doesNotContain("<x>");
        assertThat(htmlBody).contains("token=abc&quot; onclick=&quot;alert(1)&amp;next=&lt;x&gt;");
    }
}
