package com.legent.foundation.event;

import com.legent.foundation.domain.Tenant;
import com.legent.foundation.repository.TenantBootstrapStatusRepository;
import com.legent.foundation.repository.TenantRepository;
import com.legent.foundation.service.TenantBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantBootstrapInitializer {

    static final int BOOTSTRAP_PAGE_SIZE = 200;

    private final TenantRepository tenantRepository;
    private final TenantBootstrapStatusRepository bootstrapStatusRepository;
    private final TenantBootstrapService tenantBootstrapService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeBootstrapStatus() {
        Pageable pageable = PageRequest.of(0, BOOTSTRAP_PAGE_SIZE);
        Slice<Tenant> tenantPage;
        do {
            tenantPage = tenantRepository.findByStatusAndDeletedAtIsNullOrderByIdAsc(
                    Tenant.TenantStatus.ACTIVE,
                    pageable);
            tenantPage.stream()
                    .filter(tenant -> !bootstrapStatusRepository.existsById(tenant.getId()))
                    .forEach(tenant -> {
                        log.info("Queue bootstrap for tenant {}", tenant.getId());
                        tenantBootstrapService.requestBootstrap(
                                tenant.getId(),
                                tenant.getName(),
                                tenant.getSlug(),
                                false);
                    });
            pageable = tenantPage.nextPageable();
        } while (tenantPage.hasNext());
    }
}
