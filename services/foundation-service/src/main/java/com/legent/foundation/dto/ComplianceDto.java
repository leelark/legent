package com.legent.foundation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

public class ComplianceDto {

    @Getter
    @Setter
    public static class AuditEvidenceRequest {
        private String workspaceId;
        @NotBlank
        private String eventType;
        @NotBlank
        private String resourceType;
        private String resourceId;
        private String retentionCategory;
        private Boolean legalHold;
        private Map<String, Object> payload;
    }

    @Getter
    @Setter
    public static class RetentionPolicyRequest {
        private String workspaceId;
        @NotBlank
        private String dataDomain;
        @NotBlank
        private String resourceType;
        private Integer retentionDays;
        private String disposition;
        private String legalBasis;
        private String policyVersion;
        private Boolean active;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class ConsentLedgerRequest {
        private String workspaceId;
        @NotBlank
        private String subjectId;
        @Email
        private String email;
        @NotBlank
        private String channel;
        @NotBlank
        private String purpose;
        @NotBlank
        private String status;
        private String source;
        private String evidenceRef;
        private Instant occurredAt;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class PrivacyRequest {
        private String workspaceId;
        @NotBlank
        private String subjectId;
        @Email
        private String email;
        @NotBlank
        private String requestType;
        private Instant dueAt;
        private String assignedTo;
        private Map<String, Object> evidence;
        private String notes;
    }

    @Getter
    @Setter
    public static class PrivacyStatusRequest {
        @NotBlank
        private String status;
        private String resultUri;
        private Map<String, Object> evidence;
        private String notes;
    }

    @Getter
    @Setter
    public static class ComplianceExportRequest {
        private String workspaceId;
        @NotBlank
        private String exportType;
        private String format;
        private Map<String, Object> filters;
    }
}
