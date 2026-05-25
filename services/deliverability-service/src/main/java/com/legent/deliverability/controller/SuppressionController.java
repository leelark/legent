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
import com.legent.common.security.InternalServiceIdentity;
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

    private static final Set<String> ALLOWED_SUPPRESSION_LIST_SERVICES = Set.of("campaign-service");
    private static final Set<String> ALLOWED_SUPPRESSION_HISTORY_SERVICES = Set.of("campaign-service");
    private static final Set<String> ALLOWED_SUPPRESSION_CHECK_SERVICES = Set.of("audience-service");
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
            @RequestHeader(name = InternalServiceIdentity.HEADER_SERVICE, required = false) String internalService,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, required = false) String signatureTimestamp,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE, required = false) String signature,
            @RequestParam(required = false) Integer limit) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        requireInternalIdentity(
                token,
                internalService,
                signatureTimestamp,
                signature,
                ALLOWED_SUPPRESSION_LIST_SERVICES,
                tenantId,
                workspaceId,
                InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_LIST_READ);
        return ApiResponse.ok(suppressionRepository.findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(
                tenantId,
                workspaceId,
                PageRequest.of(0, boundedListLimit(limit))));
    }

    @GetMapping("/internal/history")
    public ApiResponse<Map<String, Object>> suppressionHistoryInternal(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SERVICE, required = false) String internalService,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, required = false) String signatureTimestamp,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE, required = false) String signature) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        requireInternalIdentity(
                token,
                internalService,
                signatureTimestamp,
                signature,
                ALLOWED_SUPPRESSION_HISTORY_SERVICES,
                tenantId,
                workspaceId,
                InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_HISTORY_READ);
        return ApiResponse.ok(suppressionHistoryData());
    }

    @PostMapping("/internal/check")
    public ApiResponse<SuppressionCheckResponse> checkSuppressionsInternal(
            @RequestHeader(name = "X-Internal-Token", required = false) String token,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SERVICE, required = false) String internalService,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE_TIMESTAMP, required = false) String signatureTimestamp,
            @RequestHeader(name = InternalServiceIdentity.HEADER_SIGNATURE, required = false) String signature,
            @RequestBody(required = false) SuppressionCheckRequest request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        requireInternalIdentity(
                token,
                internalService,
                signatureTimestamp,
                signature,
                ALLOWED_SUPPRESSION_CHECK_SERVICES,
                tenantId,
                workspaceId,
                InternalServiceIdentity.ACTION_DELIVERABILITY_SUPPRESSION_BULK_CHECK);
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
        return ApiResponse.ok(suppressionHistoryData());
    }

    private Map<String, Object> suppressionHistoryData() {
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
        return result;
    }

    private void requireInternalIdentity(String token,
                                         String internalService,
                                         String signatureTimestamp,
                                         String signature,
                                         Set<String> allowedServices,
                                         String tenantId,
                                         String workspaceId,
                                         String action) {
        if (!InternalServiceIdentity.matches(
                internalApiToken,
                token,
                internalService,
                allowedServices,
                tenantId,
                workspaceId,
                action,
                signatureTimestamp,
                signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal service identity");
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
