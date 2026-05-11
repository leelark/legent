package com.legent.deliverability.controller;

import java.util.List;

import com.legent.common.dto.ApiResponse;
import com.legent.deliverability.domain.SuppressionList;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.security.TenantContext;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


@RestController
@RequestMapping("/api/v1/deliverability/suppressions")
@RequiredArgsConstructor
public class SuppressionController {

    private final SuppressionListRepository suppressionRepository;
    @Value("${legent.internal.api-token}")
    private String internalApiToken;

    @PostConstruct
    void validateInternalApiToken() {
        if (internalApiToken == null || internalApiToken.isBlank() || isPlaceholderToken(internalApiToken)) {
            throw new IllegalStateException("legent.internal.api-token must be configured with a non-placeholder secret");
        }
    }

    @GetMapping
    public ApiResponse<List<SuppressionList>> listSuppressions() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(suppressionRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId));
    }

    @GetMapping("/internal")
    public ApiResponse<List<SuppressionList>> listSuppressionsInternal(
            @RequestHeader(name = "X-Internal-Token", required = false) String token) {
        if (token == null || !token.equals(internalApiToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(suppressionRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId));
    }

    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> suppressionHistory() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        long complaints = suppressionRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "COMPLAINT");
        long hardBounces = suppressionRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "HARD_BOUNCE");
        long unsubscribes = suppressionRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "UNSUBSCRIBE");
        long total = suppressionRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId).size();

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("complaints", complaints);
        result.put("hardBounces", hardBounces);
        result.put("unsubscribes", unsubscribes);
        result.put("generatedAt", Instant.now());
        return ApiResponse.ok(result);
    }

    private boolean isPlaceholderToken(String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("dev-token")
                || normalized.contains("change_me")
                || normalized.contains("changeme")
                || normalized.contains("replace_in_production")
                || normalized.equals("password");
    }
}
