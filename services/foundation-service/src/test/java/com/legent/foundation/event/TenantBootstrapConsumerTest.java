package com.legent.foundation.event;

import com.legent.common.constant.AppConstants;
import com.legent.foundation.service.TenantBootstrapService;
import com.legent.kafka.model.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TenantBootstrapConsumerTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock private TenantBootstrapService tenantBootstrapService;

    private TenantBootstrapConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TenantBootstrapConsumer(tenantBootstrapService);
    }

    @Test
    void validBootstrapEventDelegatesToBootstrapService() {
        consumer.consumeBootstrap(bootstrapEnvelope());

        verify(tenantBootstrapService).bootstrapTenant(TENANT_ID, "Acme", "acme", false);
    }

    @Test
    void missingTenantIdThrowsBeforeBootstrapSideEffects() {
        EventEnvelope<Map<String, Object>> envelope = bootstrapEnvelope();
        envelope.setTenantId(" ");

        assertThatThrownBy(() -> consumer.consumeBootstrap(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId is required");

        verifyNoInteractions(tenantBootstrapService);
    }

    @Test
    void missingEventIdThrowsBeforeBootstrapSideEffects() {
        EventEnvelope<Map<String, Object>> envelope = bootstrapEnvelope();
        envelope.setEventId(null);

        assertThatThrownBy(() -> consumer.consumeBootstrap(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId is required");

        verifyNoInteractions(tenantBootstrapService);
    }

    @Test
    void malformedPayloadThrowsBeforeBootstrapSideEffects() {
        EventEnvelope<Object> envelope = EventEnvelope.<Object>builder()
                .eventId("event-1")
                .eventType(AppConstants.TOPIC_TENANT_BOOTSTRAP_REQUESTED)
                .tenantId(TENANT_ID)
                .payload("not-an-object")
                .build();

        assertThatThrownBy(() -> consumer.consumeBootstrap(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload must be an object");

        verifyNoInteractions(tenantBootstrapService);
    }

    @Test
    void payloadTenantMismatchThrowsBeforeBootstrapSideEffects() {
        EventEnvelope<Map<String, Object>> envelope = bootstrapEnvelope();
        envelope.setPayload(Map.of(
                "tenantId", "other-tenant",
                "organizationName", "Acme",
                "organizationSlug", "acme",
                "force", false));

        assertThatThrownBy(() -> consumer.consumeBootstrap(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload tenantId must match envelope tenantId");

        verifyNoInteractions(tenantBootstrapService);
    }

    private EventEnvelope<Map<String, Object>> bootstrapEnvelope() {
        return EventEnvelope.<Map<String, Object>>builder()
                .eventId("event-1")
                .eventType(AppConstants.TOPIC_TENANT_BOOTSTRAP_REQUESTED)
                .tenantId(TENANT_ID)
                .payload(Map.of(
                        "tenantId", TENANT_ID,
                        "organizationName", "Acme",
                        "organizationSlug", "acme",
                        "force", false))
                .build();
    }
}
