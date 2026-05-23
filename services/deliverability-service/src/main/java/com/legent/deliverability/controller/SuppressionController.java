package com.legent.deliverability.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.security.InternalApiTokenValidator;
import com.legent.deliverability.domain.SuppressionList;
import com.legent.deliverability.repository.SuppressionListRepository;
import com.legent.security.TenantContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/api/v1/deliverability/suppressions")
@RequiredArgsConstructor
public class SuppressionController {

    private static final int DEFAULT_SUPPRESSION_LIST_LIMIT = AppConstants.DEFAULT_PAGE_SIZE;
    private static final int MAX_SUPPRESSION_LIST_LIMIT = AppConstants.MAX_PAGE_SIZE;
    private static final int MAX_BULK_CHECK_EMAILS = AppConstants.SEND_BATCH_SIZE;

    private final SuppressionListRepository suppressionRepository;
    @Value("${legent.internal.api-token}")
    private String internalApiToken;

    @PostConstruct
    void validateInternalApiToken() {
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('deliverability:read', principal.roles)")
    public ApiResponse<List<SuppressionList>> listSuppressions(@RequestParam(required = false) Integer limit) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(suppressionRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                tenantId,
                workspaceId,
                PageRequest.of(0, boundedListLimit(limit))));
    }

    @GetMapping("/internal")
    public ApiResponse<List<SuppressionList>> listSuppressionsInternal(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestParam(required = false) Integer limit) {
        requireInternalToken(token);
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return ApiResponse.ok(suppressionRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                tenantId,
                workspaceId,
                PageRequest.of(0, boundedListLimit(limit))));
    }

    @PostMapping("/internal/check")
    public ApiResponse<SuppressionCheckResponse> checkSuppressionsInternal(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestBody(required = false) SuppressionCheckRequest request) {
        requireInternalToken(token);
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        List<String> normalizedEmails = normalizeEmailCandidates(request == null ? null : request.emails());
        if (normalizedEmails.size() > MAX_BULK_CHECK_EMAILS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many emails in suppression check");
        }
        if (normalizedEmails.isEmpty()) {
            return ApiResponse.ok(new SuppressionCheckResponse(0, 0, Set.of()));
        }

        Set<String> suppressedEmails = suppressionRepository
                .findActiveEmailsByTenantIdAndWorkspaceIdAndNormalizedEmailIn(tenantId, workspaceId, normalizedEmails)
                .stream()
                .map(SuppressionController::normalizeEmail)
                .filter(email -> email != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return ApiResponse.ok(new SuppressionCheckResponse(normalizedEmails.size(), suppressedEmails.size(), suppressedEmails));
    }

    @GetMapping("/history")
    @PreAuthorize("@rbacEvaluator.hasPermission('deliverability:read', principal.roles)")
    public ApiResponse<Map<String, Object>> suppressionHistory() {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        long complaints = suppressionRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "COMPLAINT");
        long hardBounces = suppressionRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "HARD_BOUNCE");
        long unsubscribes = suppressionRepository.countByTenantIdAndWorkspaceIdAndReason(tenantId, workspaceId, "UNSUBSCRIBE");
        long total = suppressionRepository.countByTenantIdAndWorkspaceId(tenantId, workspaceId);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("complaints", complaints);
        result.put("hardBounces", hardBounces);
        result.put("unsubscribes", unsubscribes);
        result.put("generatedAt", Instant.now());
        return ApiResponse.ok(result);
    }

    private void requireInternalToken(String token) {
        if (!InternalApiTokenValidator.matches(internalApiToken, token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
    }

    private static int boundedListLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_SUPPRESSION_LIST_LIMIT;
        }
        return Math.min(limit, MAX_SUPPRESSION_LIST_LIMIT);
    }

    private static List<String> normalizeEmailCandidates(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }
        return emails.stream()
                .map(SuppressionController::normalizeEmail)
                .filter(email -> email != null)
                .distinct()
                .toList();
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    public record SuppressionCheckRequest(List<String> emails) {
    }

    public record SuppressionCheckResponse(int checkedCount, int suppressedCount, Set<String> suppressedEmails) {
    }

}
