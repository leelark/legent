package com.legent.platform.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.WebhookConfig;
import com.legent.platform.repository.WebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import com.legent.security.TenantContext;

@RestController
@RequestMapping({"/api/v1/platform/webhooks", "/api/v1/admin/webhooks"})
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'PLATFORM_ADMIN', 'ORG_ADMIN')")
public class WebhookController {

    private final WebhookConfigRepository webhookRepository;

    @GetMapping
    public ApiResponse<List<WebhookConfig>> listWebhooks() {
        String tenantId = TenantContext.requireTenantId();
        return ApiResponse.ok(webhookRepository.findByTenantIdAndIsActiveTrue(tenantId));
    }

    @PostMapping
    public ApiResponse<WebhookConfig> createWebhook(@RequestBody WebhookConfig hook) {
        String tenantId = TenantContext.requireTenantId();
        hook.setId(UUID.randomUUID().toString());
        hook.setTenantId(tenantId);
        return ApiResponse.ok(webhookRepository.save(hook));
    }
}
