package com.legent.foundation.event;

import com.legent.common.constant.AppConstants;
import com.legent.foundation.service.TenantBootstrapService;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantBootstrapConsumer {

    private final TenantBootstrapService tenantBootstrapService;

    @KafkaListener(topics = AppConstants.TOPIC_TENANT_BOOTSTRAP_REQUESTED, groupId = AppConstants.GROUP_FOUNDATION_BOOTSTRAP)
    public void consumeBootstrap(EventEnvelope<?> envelope) {
        if (envelope == null || envelope.getTenantId() == null || envelope.getTenantId().isBlank()) {
            log.warn("Skip bootstrap event: invalid envelope");
            return;
        }

        try {
            Map<String, Object> payload = envelope.getPayload() instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            String tenantId = envelope.getTenantId();
            String organizationName = payload.get("organizationName") instanceof String value ? value : null;
            String organizationSlug = payload.get("organizationSlug") instanceof String value ? value : null;
            boolean force = payload.get("force") instanceof Boolean value && value;
            tenantBootstrapService.bootstrapTenant(tenantId, organizationName, organizationSlug, force);
        } catch (Exception ex) {
            log.error("Failed bootstrap request eventId={}", envelope.getEventId(), ex);
            throw ex;
        }
    }
}
