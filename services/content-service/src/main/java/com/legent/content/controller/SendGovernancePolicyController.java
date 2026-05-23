package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.common.dto.ApiResponse;
import com.legent.common.dto.PagedResponse;
import com.legent.common.security.InternalApiTokenValidator;
import com.legent.content.domain.SendGovernancePolicy;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.service.SendGovernancePolicyService;
import com.legent.security.TenantContext;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(AppConstants.API_BASE_PATH + "/content/send-governance-policies")
@RequiredArgsConstructor
public class SendGovernancePolicyController {

    private final SendGovernancePolicyService service;

    @Value("${legent.internal.api-token}")
    private String internalApiToken;

    @PostConstruct
    void validateInternalApiToken() {
        InternalApiTokenValidator.requireConfigured("legent.internal.api-token", internalApiToken);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.SendGovernancePolicyResponse> create(
            @Valid @RequestBody EmailStudioDto.SendGovernancePolicyRequest request) {
        return ApiResponse.ok(map(service.create(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                request)));
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission('content:read', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public PagedResponse<EmailStudioDto.SendGovernancePolicyResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int boundedPage = boundedPage(page);
        int boundedSize = boundedSize(size);
        Page<SendGovernancePolicy> policies = service.list(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                PageRequest.of(boundedPage, boundedSize));
        return PagedResponse.of(
                policies.getContent().stream().map(this::map).toList(),
                boundedPage,
                boundedSize,
                policies.getTotalElements(),
                policies.getTotalPages());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:read', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.SendGovernancePolicyResponse> get(@PathVariable String id) {
        return ApiResponse.ok(map(service.get(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public ApiResponse<EmailStudioDto.SendGovernancePolicyResponse> update(
            @PathVariable String id,
            @Valid @RequestBody EmailStudioDto.SendGovernancePolicyRequest request) {
        return ApiResponse.ok(map(service.update(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                id,
                request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacEvaluator.hasPermission('content:delete', principal.roles) or @rbacEvaluator.hasPermission('content:*', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
    public void delete(@PathVariable String id) {
        service.delete(TenantContext.requireTenantId(), TenantContext.requireWorkspaceId(), id);
    }

    @GetMapping("/{id}/internal")
    @PreAuthorize("permitAll()")
    public ApiResponse<EmailStudioDto.SendGovernancePolicyResponse> getInternal(
            @PathVariable String id,
            @RequestHeader(name = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        return ApiResponse.ok(map(service.get(
                TenantContext.requireTenantId(),
                TenantContext.requireWorkspaceId(),
                id)));
    }

    private EmailStudioDto.SendGovernancePolicyResponse map(SendGovernancePolicy policy) {
        EmailStudioDto.SendGovernancePolicyResponse response = new EmailStudioDto.SendGovernancePolicyResponse();
        response.setId(policy.getId());
        response.setPolicyKey(policy.getPolicyKey());
        response.setName(policy.getName());
        response.setDescription(policy.getDescription());
        response.setClassification(policy.getClassification() == null ? null : policy.getClassification().name());
        response.setCommercial(policy.getClassification() == SendGovernancePolicy.Classification.COMMERCIAL);
        response.setSenderProfileId(policy.getSenderProfileId());
        response.setDeliveryProfileId(policy.getDeliveryProfileId());
        response.setSendingDomain(policy.getSendingDomain());
        response.setProviderId(policy.getProviderId());
        response.setUnsubscribePolicy(policy.getUnsubscribePolicy() == null ? null : policy.getUnsubscribePolicy().name());
        response.setSuppressionRequired(policy.getSuppressionRequired());
        response.setConsentRequired(policy.getConsentRequired());
        response.setTrackingAllowed(policy.getTrackingAllowed());
        response.setSendLogRetentionDays(policy.getSendLogRetentionDays());
        response.setPublicationPolicy(policy.getPublicationPolicy());
        response.setActive(policy.getActive());
        response.setVersion(policy.getVersion());
        response.setCreatedAt(policy.getCreatedAt() == null ? null : policy.getCreatedAt().toString());
        response.setUpdatedAt(policy.getUpdatedAt() == null ? null : policy.getUpdatedAt().toString());
        return response;
    }

    private void requireInternalToken(String token) {
        if (!InternalApiTokenValidator.matches(internalApiToken, token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
    }

    private int boundedPage(int page) {
        return Math.max(page, 0);
    }

    private int boundedSize(int size) {
        if (size < 1) {
            return AppConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, AppConstants.MAX_PAGE_SIZE);
    }
}
