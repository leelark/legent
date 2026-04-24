package com.legent.foundation.event;

import com.legent.common.constant.AppConstants;
import com.legent.common.event.UserSignedUpEvent;
import com.legent.foundation.domain.Tenant;
import com.legent.foundation.repository.TenantRepository;
import com.legent.kafka.model.EventEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantProvisioningConsumer {

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = AppConstants.TOPIC_IDENTITY_USER_SIGNUP, groupId = AppConstants.GROUP_FOUNDATION_PROVISIONING)
    public void handleUserSignedUp(String message) {
        try {
            EventEnvelope<UserSignedUpEvent> envelope = objectMapper.readValue(message, 
                    new TypeReference<EventEnvelope<UserSignedUpEvent>>() {});
            
            UserSignedUpEvent event = envelope.getPayload();
            String tenantId = envelope.getTenantId();

            log.info("Provisioning tenant for signup: tenantId={}, company={}", tenantId, event.getCompanyName());

            if (tenantRepository.existsById(tenantId)) {
                log.warn("Tenant {} already exists, skipping provisioning", tenantId);
                return;
            }

            Tenant tenant = new Tenant();
            tenant.setId(tenantId);
            tenant.setName(event.getCompanyName());
            tenant.setSlug(event.getSlug());
            tenant.setStatus(Tenant.TenantStatus.ACTIVE);
            tenant.setPlan("STARTER");

            tenantRepository.save(tenant);
            log.info("Tenant provisioned successfully: {}", tenantId);

        } catch (Exception e) {
            log.error("Failed to process tenant provisioning for message: {}", message, e);
        }
    }
}
