package com.legent.content.service;

import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.SendGovernancePolicy;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.SendGovernancePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SendGovernancePolicyService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_.-]{0,127}$");
    private static final Pattern REF_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,127}$");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("^(?=.{1,253}$)([a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}$");

    private final SendGovernancePolicyRepository repository;

    @Transactional
    public SendGovernancePolicy create(String tenantId, String workspaceId, EmailStudioDto.SendGovernancePolicyRequest request) {
        String policyKey = normalizeKey(request.getPolicyKey());
        if (repository.existsByTenantIdAndWorkspaceIdAndPolicyKeyIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, policyKey)) {
            throw new ConflictException("Send governance policy already exists: " + policyKey);
        }
        SendGovernancePolicy policy = new SendGovernancePolicy();
        policy.setTenantId(requireScope("tenantId", tenantId));
        policy.setWorkspaceId(requireScope("workspaceId", workspaceId));
        apply(policy, request);
        return repository.save(policy);
    }

    @Transactional
    public SendGovernancePolicy update(String tenantId, String workspaceId, String id, EmailStudioDto.SendGovernancePolicyRequest request) {
        SendGovernancePolicy policy = get(tenantId, workspaceId, id);
        String requestedKey = request.getPolicyKey() == null ? policy.getPolicyKey() : normalizeKey(request.getPolicyKey());
        if (!requestedKey.equalsIgnoreCase(policy.getPolicyKey())
                && repository.existsByTenantIdAndWorkspaceIdAndPolicyKeyIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, requestedKey)) {
            throw new ConflictException("Send governance policy already exists: " + requestedKey);
        }
        apply(policy, request);
        return repository.save(policy);
    }

    @Transactional(readOnly = true)
    public SendGovernancePolicy get(String tenantId, String workspaceId, String id) {
        return repository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                        requireScope("id", id),
                        requireScope("tenantId", tenantId),
                        requireScope("workspaceId", workspaceId))
                .orElseThrow(() -> new NotFoundException("SendGovernancePolicy", id));
    }

    @Transactional(readOnly = true)
    public Page<SendGovernancePolicy> list(String tenantId, String workspaceId, Pageable pageable) {
        return repository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                requireScope("tenantId", tenantId),
                requireScope("workspaceId", workspaceId),
                pageable);
    }

    @Transactional
    public void delete(String tenantId, String workspaceId, String id) {
        SendGovernancePolicy policy = get(tenantId, workspaceId, id);
        policy.softDelete();
        repository.save(policy);
    }

    private void apply(SendGovernancePolicy policy, EmailStudioDto.SendGovernancePolicyRequest request) {
        if (request.getPolicyKey() != null) {
            policy.setPolicyKey(normalizeKey(request.getPolicyKey()));
        }
        if (request.getName() != null) {
            policy.setName(requireScope("name", request.getName()).trim());
        }
        if (request.getDescription() != null) {
            policy.setDescription(blankToNull(request.getDescription()));
        }
        if (request.getClassification() != null) {
            policy.setClassification(parseEnum("classification", request.getClassification(), SendGovernancePolicy.Classification.class));
        }
        if (request.getSenderProfileId() != null) {
            policy.setSenderProfileId(normalizeReference("senderProfileId", request.getSenderProfileId()));
        }
        if (request.getDeliveryProfileId() != null) {
            policy.setDeliveryProfileId(normalizeReference("deliveryProfileId", request.getDeliveryProfileId()));
        }
        if (request.getSendingDomain() != null) {
            policy.setSendingDomain(normalizeDomain(request.getSendingDomain()));
        }
        if (request.getProviderId() != null) {
            policy.setProviderId(normalizeReference("providerId", request.getProviderId()));
        }
        if (request.getUnsubscribePolicy() != null) {
            policy.setUnsubscribePolicy(parseEnum("unsubscribePolicy", request.getUnsubscribePolicy(), SendGovernancePolicy.UnsubscribePolicy.class));
        }
        if (request.getSuppressionRequired() != null) {
            policy.setSuppressionRequired(request.getSuppressionRequired());
        }
        if (request.getConsentRequired() != null) {
            policy.setConsentRequired(request.getConsentRequired());
        }
        if (request.getTrackingAllowed() != null) {
            policy.setTrackingAllowed(request.getTrackingAllowed());
        }
        if (request.getSendLogRetentionDays() != null) {
            validateRetention(request.getSendLogRetentionDays());
            policy.setSendLogRetentionDays(request.getSendLogRetentionDays());
        }
        if (request.getPublicationPolicy() != null) {
            policy.setPublicationPolicy(normalizeReference("publicationPolicy", request.getPublicationPolicy()).toUpperCase(Locale.ROOT));
        }
        if (request.getActive() != null) {
            policy.setActive(request.getActive());
        }
        validatePolicy(policy);
    }

    private void validatePolicy(SendGovernancePolicy policy) {
        if (policy.getName() == null || policy.getName().isBlank()) {
            throw new ValidationException("name", "Name is required");
        }
        if (policy.getClassification() == SendGovernancePolicy.Classification.COMMERCIAL) {
            if (!Boolean.TRUE.equals(policy.getSuppressionRequired())) {
                throw new ValidationException("suppressionRequired", "Commercial send policies must require suppression checks");
            }
            if (policy.getUnsubscribePolicy() != SendGovernancePolicy.UnsubscribePolicy.REQUIRED) {
                throw new ValidationException("unsubscribePolicy", "Commercial send policies must require unsubscribe handling");
            }
        }
        validateRetention(policy.getSendLogRetentionDays());
    }

    private void validateRetention(Integer days) {
        if (days == null || days < 1 || days > 2555) {
            throw new ValidationException("sendLogRetentionDays", "Retention must be between 1 and 2555 days");
        }
    }

    private String normalizeKey(String value) {
        String key = requireScope("policyKey", value).trim();
        if (!KEY_PATTERN.matcher(key).matches()) {
            throw new ValidationException("policyKey", "Use letters, numbers, dots, underscores, and dashes; key must start with a letter");
        }
        return key;
    }

    private String normalizeReference(String field, String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        if (!REF_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(field, "Use letters, numbers, dots, underscores, dashes, or colons");
        }
        return normalized;
    }

    private String normalizeDomain(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!DOMAIN_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("sendingDomain", "Use a valid domain such as example.com");
        }
        return normalized;
    }

    private <T extends Enum<T>> T parseEnum(String field, String value, Class<T> type) {
        try {
            return Enum.valueOf(type, requireScope(field, value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException(field, "Unsupported value: " + value);
        }
    }

    private String requireScope(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, field + " is required");
        }
        return value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
