package com.legent.delivery.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.legent.common.dto.ApiResponse;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.adapter.impl.SmtpProviderAdapter;
import com.legent.delivery.dto.SmtpProviderDto;
import com.legent.delivery.repository.SmtpProviderRepository;
import com.legent.delivery.service.CredentialEncryptionService;
import com.legent.delivery.service.ProviderHealthMonitoringService;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final SmtpProviderRepository repository;
    private final CredentialEncryptionService encryptionService;
    private final SmtpProviderAdapter smtpProviderAdapter;
    private final ProviderHealthMonitoringService healthMonitoringService;

    @GetMapping
    public ApiResponse<List<SmtpProviderDto.Response>> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        String tenantId = TenantContext.getTenantId();
        List<SmtpProvider> providers = includeInactive
            ? repository.findByTenantIdOrderByPriorityAsc(tenantId)
            : repository.findByTenantIdAndIsActiveTrueOrderByPriorityAsc(tenantId);
        List<SmtpProviderDto.Response> responses = providers.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
        return ApiResponse.ok(responses);
    }

    @GetMapping("/health")
    public ApiResponse<List<SmtpProviderDto.ProviderHealthResponse>> health() {
        String tenantId = TenantContext.getTenantId();
        List<SmtpProvider> providers = repository.findByTenantIdOrderByPriorityAsc(tenantId);
        List<SmtpProviderDto.ProviderHealthResponse> responses = providers.stream()
                .map(provider -> SmtpProviderDto.ProviderHealthResponse.builder()
                        .id(provider.getId())
                        .name(provider.getName())
                        .type(provider.getType())
                        .isActive(provider.isActive())
                        .healthStatus(provider.getHealthStatus())
                        .lastHealthCheckAt(provider.getLastHealthCheckAt())
                        .priority(provider.getPriority())
                        .build())
                .toList();
        return ApiResponse.ok(responses);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<SmtpProviderDto.Response> testProvider(@PathVariable String id) {
        SmtpProvider provider = repository.findById(id)
                .filter(p -> p.getTenantId().equals(TenantContext.getTenantId()))
                .orElseThrow(() -> new RuntimeException("Provider not found"));
        healthMonitoringService.checkProviderHealth(provider);
        SmtpProvider refreshed = repository.findById(id).orElse(provider);
        return ApiResponse.ok(mapToResponse(refreshed));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SmtpProviderDto.Response> create(@RequestBody SmtpProviderDto.CreateRequest request) {
        String tenantId = TenantContext.getTenantId();
        SmtpProvider provider = new SmtpProvider();
        provider.setTenantId(tenantId);
        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setHost(request.getHost());
        provider.setPort(request.getPort());
        provider.setUsername(request.getUsername());
        // Encrypt password before storing
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            var encrypted = encryptionService.encrypt(request.getPassword());
            provider.setEncryptedPassword(encrypted.encryptedData());
            provider.setEncryptionIv(encrypted.iv());
        }
        provider.setPriority(request.getPriority() != null ? request.getPriority() : 1);
        provider.setMaxSendRate(request.getMaxSendRate());
        provider.setActive(request.getIsActive() == null || request.getIsActive());
        if (request.getHealthCheckEnabled() != null) {
            provider.setHealthCheckEnabled(request.getHealthCheckEnabled());
        }
        provider.setHealthCheckUrl(request.getHealthCheckUrl());
        if (request.getHealthCheckIntervalSeconds() != null) {
            provider.setHealthCheckIntervalSeconds(request.getHealthCheckIntervalSeconds());
        }

        provider = repository.save(provider);
        return ApiResponse.ok(mapToResponse(provider));
    }

    @PutMapping("/{id}")
    public ApiResponse<SmtpProviderDto.Response> update(@PathVariable String id, @RequestBody SmtpProviderDto.CreateRequest request) {
        SmtpProvider provider = repository.findById(id)
            .filter(p -> p.getTenantId().equals(TenantContext.getTenantId()))
            .orElseThrow(() -> new RuntimeException("Provider not found"));

        provider.setName(request.getName());
        provider.setType(request.getType());
        provider.setHost(request.getHost());
        provider.setPort(request.getPort());
        provider.setUsername(request.getUsername());
        // Encrypt password before storing
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            var encrypted = encryptionService.encrypt(request.getPassword());
            provider.setEncryptedPassword(encrypted.encryptedData());
            provider.setEncryptionIv(encrypted.iv());
        }
        if (request.getPriority() != null) {
            provider.setPriority(request.getPriority());
        }
        provider.setMaxSendRate(request.getMaxSendRate());
        if (request.getIsActive() != null) {
            provider.setActive(request.getIsActive());
        }
        if (request.getHealthCheckEnabled() != null) {
            provider.setHealthCheckEnabled(request.getHealthCheckEnabled());
        }
        provider.setHealthCheckUrl(request.getHealthCheckUrl());
        if (request.getHealthCheckIntervalSeconds() != null) {
            provider.setHealthCheckIntervalSeconds(request.getHealthCheckIntervalSeconds());
        }

        provider = repository.save(provider);
        
        // Invalidate sender cache so new config is picked up immediately
        smtpProviderAdapter.invalidateCache(provider.getId());
        
        return ApiResponse.ok(mapToResponse(provider));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        SmtpProvider provider = repository.findById(id)
            .filter(p -> p.getTenantId().equals(TenantContext.getTenantId()))
            .orElseThrow(() -> new RuntimeException("Provider not found"));
        String providerId = provider.getId();
        repository.delete(provider);
        
        // Invalidate sender cache
        smtpProviderAdapter.invalidateCache(providerId);
        
        return ApiResponse.ok(null);
    }

    private SmtpProviderDto.Response mapToResponse(SmtpProvider p) {
        return SmtpProviderDto.Response.builder()
            .id(p.getId())
            .name(p.getName())
            .type(p.getType())
            .host(p.getHost())
            .port(p.getPort())
            .username(p.getUsername())
            .isActive(p.isActive())
            .priority(p.getPriority())
            .maxSendRate(p.getMaxSendRate())
            .healthCheckEnabled(p.isHealthCheckEnabled())
            .healthCheckUrl(p.getHealthCheckUrl())
            .healthCheckIntervalSeconds(p.getHealthCheckIntervalSeconds())
            .healthStatus(p.getHealthStatus())
            .lastHealthCheckAt(p.getLastHealthCheckAt())
            .createdAt(p.getCreatedAt())
            .build();
    }
}
