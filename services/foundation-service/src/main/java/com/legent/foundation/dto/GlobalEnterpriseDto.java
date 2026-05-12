package com.legent.foundation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class GlobalEnterpriseDto {

    @Getter
    @Setter
    public static class OperatingModelRequest {
        private String workspaceId;
        @NotBlank
        private String modelKey;
        @NotBlank
        private String name;
        private String topologyMode;
        private String status;
        @NotBlank
        private String primaryRegion;
        private List<String> standbyRegions;
        private List<String> activeRegions;
        @Min(0)
        private Integer rpoTargetMinutes;
        @Min(0)
        private Integer rtoTargetMinutes;
        private Map<String, Object> trafficPolicy;
        private String promotionState;
        private String failoverState;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class FailoverDrillRequest {
        private String workspaceId;
        private String operatingModelId;
        private String drillType;
        private String sourceRegion;
        private String targetRegion;
        private List<String> affectedServices;
        @Min(0)
        private Integer plannedRpoMinutes;
        @Min(0)
        private Integer plannedRtoMinutes;
        @Min(0)
        private Integer actualRpoMinutes;
        @Min(0)
        private Integer actualRtoMinutes;
        private List<Map<String, Object>> findings;
        private Map<String, Object> evidence;
        private Instant completedAt;
    }

    @Getter
    @Setter
    public static class FailoverEvaluationRequest {
        private String workspaceId;
        @NotBlank
        private String dataClass;
        @NotBlank
        private String sourceRegion;
        @NotBlank
        private String targetRegion;
        private String topologyMode;
        private String operatingModelKey;
        private Map<String, Object> context;
    }

    @Getter
    @Setter
    public static class DataResidencyPolicyRequest {
        private String workspaceId;
        @NotBlank
        private String policyKey;
        @NotBlank
        private String dataClass;
        @NotBlank
        private String homeRegion;
        private List<String> allowedRegions;
        private List<String> blockedRegions;
        private Boolean failoverAllowed;
        private String legalBasis;
        private String enforcementMode;
        private String status;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class EncryptionPolicyRequest {
        private String workspaceId;
        @NotBlank
        private String policyKey;
        @NotBlank
        private String dataClass;
        @NotBlank
        private String keyProvider;
        @NotBlank
        private String keyRef;
        private String algorithm;
        @Min(1)
        @Max(3650)
        private Integer rotationDays;
        private String residencyPolicyId;
        private String status;
        private Instant lastRotatedAt;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class LegalHoldRequest {
        private String workspaceId;
        @NotBlank
        private String holdKey;
        @NotBlank
        private String subjectType;
        @NotBlank
        private String subjectId;
        private List<String> dataDomains;
        @NotBlank
        private String reason;
        private Map<String, Object> evidence;
    }

    @Getter
    @Setter
    public static class LegalHoldReleaseRequest {
        @NotBlank
        private String releaseReason;
        private Map<String, Object> evidence;
    }

    @Getter
    @Setter
    public static class LineageEdgeRequest {
        private String workspaceId;
        @NotBlank
        private String sourceType;
        @NotBlank
        private String sourceId;
        @NotBlank
        private String targetType;
        @NotBlank
        private String targetId;
        @NotBlank
        private String dataClass;
        private String transformType;
        private String purpose;
        private List<String> policyRefs;
        @Min(0)
        @Max(1)
        private Double confidence;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class PolicySimulationRequest {
        private String workspaceId;
        @NotBlank
        private String simulationKey;
        @NotBlank
        private String policyType;
        @NotBlank
        private String artifactType;
        private String artifactId;
        private Map<String, Object> inputContext;
    }

    @Getter
    @Setter
    public static class EvidencePackRequest {
        private String workspaceId;
        @NotBlank
        private String packKey;
        @NotBlank
        private String name;
        private Map<String, Object> scope;
        private List<String> evidenceRefs;
        private Instant expiresAt;
    }

    @Getter
    @Setter
    public static class ConnectorTemplateRequest {
        @NotBlank
        private String connectorKey;
        @NotBlank
        private String category;
        @NotBlank
        private String displayName;
        @NotBlank
        private String vendor;
        private List<String> authModes;
        private List<String> supportedEvents;
        private Map<String, Object> capabilities;
        private String status;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class ConnectorInstanceRequest {
        private String workspaceId;
        private String templateId;
        @NotBlank
        private String instanceKey;
        @NotBlank
        private String connectorKey;
        @NotBlank
        private String displayName;
        @NotBlank
        private String category;
        @NotBlank
        private String authMode;
        private String credentialRef;
        private String status;
        private Map<String, Object> config;
    }

    @Getter
    @Setter
    public static class SyncJobRequest {
        private String workspaceId;
        @NotBlank
        private String connectorInstanceId;
        @NotBlank
        private String syncType;
        private String direction;
        private Boolean dryRun;
        private Map<String, Object> request;
    }

    @Getter
    @Setter
    public static class OptimizationPolicyRequest {
        private String workspaceId;
        @NotBlank
        private String policyKey;
        @NotBlank
        private String name;
        private String mode;
        private String status;
        private Map<String, Object> targetScope;
        private Map<String, Object> constraints;
        private Map<String, Object> guardrails;
        private Map<String, Object> rollbackPolicy;
    }

    @Getter
    @Setter
    public static class OptimizationRecommendationRequest {
        private String workspaceId;
        @NotBlank
        private String policyKey;
        @NotBlank
        private String artifactType;
        @NotBlank
        private String artifactId;
        private Map<String, Object> inputSignals;
        private Map<String, Object> recommendation;
        private Map<String, Object> targetSnapshot;
    }

    @Getter
    @Setter
    public static class OptimizationDecisionRequest {
        @NotBlank
        private String decision;
        private String decisionNote;
        private Map<String, Object> appliedSnapshot;
    }

    @Getter
    @Setter
    public static class OptimizationRollbackRequest {
        @NotBlank
        private String recommendationId;
        @NotBlank
        private String reason;
        private Map<String, Object> evidence;
    }
}
