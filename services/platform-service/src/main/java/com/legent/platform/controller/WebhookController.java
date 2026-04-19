package com.legent.platform.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.platform.domain.WebhookConfig;
import com.legent.platform.repository.WebhookConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookConfigRepository webhookRepository;

    @GetMapping
    public ApiResponse<List<WebhookConfig>> listWebhooks(@RequestHeader("X-Tenant-Id") String tenantId) {
        return ApiResponse.ok(webhookRepository.findByTenantIdAndIsActiveTrue(tenantId));
    }

    @PostMapping
    public ApiResponse<WebhookConfig> createWebhook(@RequestHeader("X-Tenant-Id") String tenantId, @RequestBody WebhookConfig hook) {
        hook.setId(UUID.randomUUID().toString());
        hook.setTenantId(tenantId);
        return ApiResponse.ok(webhookRepository.save(hook));
    }
}
