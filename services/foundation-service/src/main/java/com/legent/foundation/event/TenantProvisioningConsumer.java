package com.legent.foundation.event;

import com.legent.common.constant.AppConstants;
import com.legent.common.event.UserSignedUpEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.foundation.domain.Tenant;
import com.legent.foundation.repository.TenantRepository;
import com.legent.foundation.service.TenantBootstrapService;
import com.legent.kafka.model.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantProvisioningConsumer {

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;
    private final TenantBootstrapService tenantBootstrapService;

    @Transactional
    @KafkaListener(topics = AppConstants.TOPIC_IDENTITY_USER_SIGNUP, groupId = AppConstants.GROUP_FOUNDATION_PROVISIONING)
    public void handleUserSignedUp(EventEnvelope<?> envelope) {
        try {
            if (envelope == null) {
                log.warn("Invalid signup envelope received: null envelope");
                return;
            }

            UserSignedUpEvent event = objectMapper.convertValue(envelope.getPayload(), UserSignedUpEvent.class);
            String tenantId = envelope.getTenantId();
            if (event == null || tenantId == null || tenantId.isBlank()) {
                log.warn("Invalid signup envelope received: eventId={}", envelope != null ? envelope.getEventId() : "unknown");
                return;
            }

            log.info("Provisioning tenant for signup: tenantId={}, company={}", tenantId, event.getCompanyName());

            if (tenantRepository.existsById(tenantId)) {
                log.warn("Tenant {} already exists, skipping provisioning", tenantId);
                return;
            }

            Tenant tenant = new Tenant();
            tenant.setId(tenantId);
            tenant.setName(event.getCompanyName());
            tenant.setSlug(resolveUniqueSlug(event, tenantId));
            tenant.setStatus(Tenant.TenantStatus.ACTIVE);
            tenant.setPlan("STARTER");

            try {
                tenantRepository.save(tenant);
            } catch (DataIntegrityViolationException duplicateSlug) {
                // Retry once with deterministic tenant-suffixed slug for concurrent collisions.
                tenant.setSlug(fallbackSlug(tenant.getSlug(), tenantId));
                tenantRepository.save(tenant);
            }
            log.info("Tenant provisioned successfully: {}", tenantId);
            tenantBootstrapService.requestBootstrap(tenantId, tenant.getName(), tenant.getSlug(), false);

        } catch (Exception e) {
            log.error("Failed to process tenant provisioning for eventId={}", envelope != null ? envelope.getEventId() : "unknown", e);
        }
    }

    private String resolveUniqueSlug(UserSignedUpEvent event, String tenantId) {
        String base = sanitizeSlug(event != null ? event.getSlug() : null);
        if (base == null) {
            base = sanitizeSlug(event != null ? event.getCompanyName() : null);
        }
        if (base == null) {
            base = "tenant";
        }
        if (!tenantRepository.existsBySlug(base)) {
            return base;
        }
        String candidate = fallbackSlug(base, tenantId);
        if (!tenantRepository.existsBySlug(candidate)) {
            return candidate;
        }
        return candidate + "-1";
    }

    private String sanitizeSlug(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        value = value.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        return value.isBlank() ? null : value;
    }

    private String fallbackSlug(String base, String tenantId) {
        String seed = tenantId == null ? "tenant" : tenantId.replaceAll("[^a-zA-Z0-9]", "");
        if (seed.length() > 8) {
            seed = seed.substring(seed.length() - 8);
        }
        String normalizedBase = sanitizeSlug(base);
        if (normalizedBase == null) {
            normalizedBase = "tenant";
        }
        return normalizedBase + "-" + seed.toLowerCase();
    }
}
