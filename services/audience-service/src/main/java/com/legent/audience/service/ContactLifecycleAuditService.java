package com.legent.audience.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.legent.audience.domain.ContactLifecycleAudit;
import com.legent.audience.domain.DataExtension;
import com.legent.audience.domain.Subscriber;
import com.legent.audience.domain.Suppression;
import com.legent.audience.repository.ContactLifecycleAuditRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactLifecycleAuditService {

    private static final String OUTCOME_SUCCEEDED = "SUCCEEDED";
    private static final String SOURCE_AUDIENCE_SERVICE = "AUDIENCE_SERVICE";

    private final ContactLifecycleAuditRepository auditRepository;

    public void subscriberDeleted(Subscriber subscriber, String source) {
        record(AuditRequest.builder()
                .tenantId(subscriber.getTenantId())
                .workspaceId(subscriber.getWorkspaceId())
                .subjectType("SUBSCRIBER")
                .subjectId(subscriber.getId())
                .subscriberId(subscriber.getId())
                .email(subscriber.getEmail())
                .action("SUBSCRIBER_DELETED")
                .source(source)
                .metadata(Map.of(
                        "status", safeEnumName(subscriber.getStatus()),
                        "subscriberKeyPresent", subscriber.getSubscriberKey() != null && !subscriber.getSubscriberKey().isBlank()))
                .build());
    }

    public void subscribersBulkDeleted(String tenantId, String workspaceId, long deletedCount, long requestedCount) {
        record(AuditRequest.builder()
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .subjectType("SUBSCRIBER")
                .action("SUBSCRIBER_BULK_DELETED")
                .source("SUBSCRIBER_BULK_ACTION")
                .metadata(Map.of(
                        "deletedCount", deletedCount,
                        "requestedCount", requestedCount))
                .build());
    }

    public void suppressionCreated(Suppression suppression, String source) {
        suppressionChanged(suppression, "SUPPRESSION_CREATED", source);
    }

    public void suppressionsBulkCreated(String tenantId, String workspaceId, long createdCount, long requestedCount) {
        record(AuditRequest.builder()
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .subjectType("SUPPRESSION")
                .action("SUPPRESSION_BULK_CREATED")
                .source("SUPPRESSION_SERVICE")
                .metadata(Map.of(
                        "createdCount", createdCount,
                        "requestedCount", requestedCount))
                .build());
    }

    public void suppressionDeleted(Suppression suppression, String source) {
        suppressionChanged(suppression, "SUPPRESSION_DELETED", source);
    }

    public void suppressionRecovered(Suppression suppression, String source) {
        suppressionChanged(suppression, "SUPPRESSION_RECOVERED", source);
    }

    public void preferenceUpdated(Subscriber subscriber, String action, Map<String, Object> metadata) {
        record(AuditRequest.builder()
                .tenantId(subscriber.getTenantId())
                .workspaceId(subscriber.getWorkspaceId())
                .subjectType("PREFERENCE")
                .subjectId(subscriber.getId())
                .subscriberId(subscriber.getId())
                .email(subscriber.getEmail())
                .action(action)
                .source("PREFERENCE_CENTER")
                .metadata(metadata)
                .build());
    }

    public void dataExtensionRetentionUpdated(DataExtension dataExtension) {
        record(AuditRequest.builder()
                .tenantId(dataExtension.getTenantId())
                .workspaceId(dataExtension.getWorkspaceId())
                .subjectType("DATA_EXTENSION")
                .subjectId(dataExtension.getId())
                .dataExtensionId(dataExtension.getId())
                .action("DATA_EXTENSION_RETENTION_UPDATED")
                .source(SOURCE_AUDIENCE_SERVICE)
                .metadata(Map.of(
                        "retentionAction", nullSafe(dataExtension.getRetentionAction()),
                        "retentionDays", dataExtension.getRetentionDays() == null ? "" : dataExtension.getRetentionDays(),
                        "dataClassification", nullSafe(dataExtension.getDataClassification())))
                .build());
    }

    public void dataExtensionDeleted(DataExtension dataExtension) {
        record(AuditRequest.builder()
                .tenantId(dataExtension.getTenantId())
                .workspaceId(dataExtension.getWorkspaceId())
                .subjectType("DATA_EXTENSION")
                .subjectId(dataExtension.getId())
                .dataExtensionId(dataExtension.getId())
                .action("DATA_EXTENSION_DELETED")
                .source(SOURCE_AUDIENCE_SERVICE)
                .metadata(Map.of(
                        "recordCount", dataExtension.getRecordCount(),
                        "sendable", dataExtension.isSendable(),
                        "dataClassification", nullSafe(dataExtension.getDataClassification())))
                .build());
    }

    public void dataExtensionRecordsOverwritten(DataExtension dataExtension, long deletedCount, long writtenCount) {
        record(AuditRequest.builder()
                .tenantId(dataExtension.getTenantId())
                .workspaceId(dataExtension.getWorkspaceId())
                .subjectType("DATA_EXTENSION")
                .subjectId(dataExtension.getId())
                .dataExtensionId(dataExtension.getId())
                .action("DATA_EXTENSION_RECORDS_OVERWRITTEN")
                .source("DATA_EXTENSION_QUERY_ACTIVITY")
                .metadata(Map.of(
                        "deletedCount", deletedCount,
                        "writtenCount", writtenCount,
                        "dataClassification", nullSafe(dataExtension.getDataClassification())))
                .build());
    }

    public void sendEligibilityChecked(String tenantId,
                                       String workspaceId,
                                       List<SendEligibilityService.EligibilityResult> results,
                                       int requestedEmailCount,
                                       int requestedSubscriberIdCount) {
        long eligibleCount = results == null ? 0 : results.stream().filter(SendEligibilityService.EligibilityResult::eligible).count();
        int totalCount = results == null ? 0 : results.size();
        record(AuditRequest.builder()
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .subjectType("SEND_ELIGIBILITY")
                .action("SEND_ELIGIBILITY_CHECKED")
                .source(SOURCE_AUDIENCE_SERVICE)
                .metadata(Map.of(
                        "requestedEmailCount", requestedEmailCount,
                        "requestedSubscriberIdCount", requestedSubscriberIdCount,
                        "resultCount", totalCount,
                        "eligibleCount", eligibleCount,
                        "ineligibleCount", Math.max(0, totalCount - eligibleCount)))
                .build());
    }

    public void record(AuditRequest request) {
        ContactLifecycleAudit audit = new ContactLifecycleAudit();
        audit.setTenantId(requireText(request.tenantId(), "tenantId"));
        audit.setWorkspaceId(requireText(request.workspaceId(), "workspaceId"));
        audit.setSubjectType(requireText(request.subjectType(), "subjectType"));
        audit.setSubjectId(blankToNull(request.subjectId()));
        audit.setSubscriberId(blankToNull(request.subscriberId()));
        audit.setDataExtensionId(blankToNull(request.dataExtensionId()));
        audit.setEmailSha256(hashEmail(request.email()));
        audit.setAction(requireText(request.action(), "action"));
        audit.setOutcome(blankToDefault(request.outcome(), OUTCOME_SUCCEEDED));
        audit.setSource(blankToDefault(request.source(), SOURCE_AUDIENCE_SERVICE));
        audit.setSourceEventId(blankToNull(request.sourceEventId()));
        audit.setIdempotencyKey(blankToNull(request.idempotencyKey()));
        audit.setOperationId(blankToNull(request.operationId()));
        audit.setPerformedBy(blankToNull(TenantContext.getUserId()));
        audit.setCreatedBy(blankToNull(TenantContext.getUserId()));
        audit.setMetadata(safeMetadata(request.metadata()));
        auditRepository.save(audit);
    }

    private void suppressionChanged(Suppression suppression, String action, String source) {
        record(AuditRequest.builder()
                .tenantId(suppression.getTenantId())
                .workspaceId(suppression.getWorkspaceId())
                .subjectType("SUPPRESSION")
                .subjectId(suppression.getId())
                .email(suppression.getEmail())
                .action(action)
                .source(source)
                .metadata(Map.of(
                        "suppressionType", safeEnumName(suppression.getSuppressionType()),
                        "recoveryStatus", nullSafe(suppression.getRecoveryStatus()),
                        "reasonPresent", suppression.getReason() != null && !suppression.getReason().isBlank()))
                .build());
    }

    private Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || isUnsafeKey(key)) {
                return;
            }
            safe.put(key, safeMetadataValue(value));
        });
        return safe;
    }

    private Object safeMetadataValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> nested) {
            Map<String, Object> safeNested = new LinkedHashMap<>();
            nested.forEach((nestedKey, nestedValue) -> {
                if (nestedKey == null || isUnsafeKey(String.valueOf(nestedKey))) {
                    return;
                }
                safeNested.put(String.valueOf(nestedKey), safeMetadataValue(nestedValue));
            });
            return safeNested;
        }
        if (value instanceof Iterable<?> iterable) {
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                    .map(this::safeMetadataValue)
                    .toList();
        }
        return value;
    }

    private boolean isUnsafeKey(String key) {
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("email")
                || normalized.equals("emailaddress")
                || normalized.equals("rawemail")
                || normalized.equals("phone")
                || normalized.equals("address")
                || normalized.contains("token")
                || normalized.contains("secret");
    }

    private String hashEmail(String email) {
        String normalized = blankToNull(email);
        if (normalized == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash audit email", ex);
        }
    }

    private String requireText(String value, String field) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required for contact lifecycle audit");
        }
        return normalized;
    }

    private String blankToDefault(String value, String defaultValue) {
        String normalized = blankToNull(value);
        return normalized == null ? defaultValue : normalized;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String safeEnumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    public record AuditRequest(
            String tenantId,
            String workspaceId,
            String subjectType,
            String subjectId,
            String subscriberId,
            String dataExtensionId,
            String email,
            String action,
            String outcome,
            String source,
            String sourceEventId,
            String idempotencyKey,
            String operationId,
            Map<String, Object> metadata) {

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String tenantId;
            private String workspaceId;
            private String subjectType;
            private String subjectId;
            private String subscriberId;
            private String dataExtensionId;
            private String email;
            private String action;
            private String outcome;
            private String source;
            private String sourceEventId;
            private String idempotencyKey;
            private String operationId;
            private Map<String, Object> metadata;

            public Builder tenantId(String tenantId) {
                this.tenantId = tenantId;
                return this;
            }

            public Builder workspaceId(String workspaceId) {
                this.workspaceId = workspaceId;
                return this;
            }

            public Builder subjectType(String subjectType) {
                this.subjectType = subjectType;
                return this;
            }

            public Builder subjectId(String subjectId) {
                this.subjectId = subjectId;
                return this;
            }

            public Builder subscriberId(String subscriberId) {
                this.subscriberId = subscriberId;
                return this;
            }

            public Builder dataExtensionId(String dataExtensionId) {
                this.dataExtensionId = dataExtensionId;
                return this;
            }

            public Builder email(String email) {
                this.email = email;
                return this;
            }

            public Builder action(String action) {
                this.action = action;
                return this;
            }

            public Builder outcome(String outcome) {
                this.outcome = outcome;
                return this;
            }

            public Builder source(String source) {
                this.source = source;
                return this;
            }

            public Builder sourceEventId(String sourceEventId) {
                this.sourceEventId = sourceEventId;
                return this;
            }

            public Builder idempotencyKey(String idempotencyKey) {
                this.idempotencyKey = idempotencyKey;
                return this;
            }

            public Builder operationId(String operationId) {
                this.operationId = operationId;
                return this;
            }

            public Builder metadata(Map<String, Object> metadata) {
                this.metadata = metadata;
                return this;
            }

            public AuditRequest build() {
                return new AuditRequest(tenantId, workspaceId, subjectType, subjectId, subscriberId,
                        dataExtensionId, email, action, outcome, source, sourceEventId, idempotencyKey,
                        operationId, metadata);
            }
        }
    }
}
