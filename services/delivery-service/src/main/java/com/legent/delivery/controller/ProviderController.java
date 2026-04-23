package com.legent.delivery.controller;

import java.util.List;
import java.util.stream.Collectors;

import com.legent.common.dto.ApiResponse;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.dto.SmtpProviderDto;
import com.legent.delivery.repository.SmtpProviderRepository;
import com.legent.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final SmtpProviderRepository repository;

    @GetMapping
    public ApiResponse<List<SmtpProviderDto.Response>> list() {
        String tenantId = TenantContext.getTenantId();
        List<SmtpProvider> providers = repository.findByTenantIdAndIsActiveTrueOrderByPriorityAsc(tenantId);
        List<SmtpProviderDto.Response> responses = providers.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
        return ApiResponse.ok(responses);
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
        provider.setPasswordHash(request.getPassword());
        provider.setPriority(request.getPriority() != null ? request.getPriority() : 1);
        provider.setMaxSendRate(request.getMaxSendRate());
        
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
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            provider.setPasswordHash(request.getPassword());
        }
        if (request.getPriority() != null) {
            provider.setPriority(request.getPriority());
        }
        provider.setMaxSendRate(request.getMaxSendRate());
        
        provider = repository.save(provider);
        return ApiResponse.ok(mapToResponse(provider));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        SmtpProvider provider = repository.findById(id)
            .filter(p -> p.getTenantId().equals(TenantContext.getTenantId()))
            .orElseThrow(() -> new RuntimeException("Provider not found"));
        repository.delete(provider);
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
            .createdAt(p.getCreatedAt())
            .build();
    }
}
