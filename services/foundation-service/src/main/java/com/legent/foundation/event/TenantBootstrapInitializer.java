package com.legent.foundation.event;

import com.legent.foundation.repository.TenantBootstrapStatusRepository;
import com.legent.foundation.repository.TenantRepository;
import com.legent.foundation.service.TenantBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantBootstrapInitializer {

    private final TenantRepository tenantRepository;
    private final TenantBootstrapStatusRepository bootstrapStatusRepository;
    private final TenantBootstrapService tenantBootstrapService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeBootstrapStatus() {
        tenantRepository.findAll().stream()
                .filter(tenant -> !bootstrapStatusRepository.existsById(tenant.getId()))
                .forEach(tenant -> {
                    log.info("Queue bootstrap for tenant {}", tenant.getId());
                    tenantBootstrapService.requestBootstrap(tenant.getId(), tenant.getName(), tenant.getSlug(), false);
                });
    }
}
