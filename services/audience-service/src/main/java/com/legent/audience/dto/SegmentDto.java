package com.legent.audience.dto;

import java.util.List;

import java.util.Map;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class SegmentDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank @Size(max = 255) private String name;
        @Size(max = 2000) private String description;
        private String segmentType;
        @NotNull private Map<String, Object> rules;
        private boolean scheduleEnabled;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 255) private String name;
        @Size(max = 2000) private String description;
        private Map<String, Object> rules;
        private Boolean scheduleEnabled;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private String id;
        private String name;
        private String description;
        private String status;
        private String segmentType;
        private String derivationMode;
        private Map<String, Object> rules;
        private Map<String, Object> predictiveGovernance;
        private long memberCount;
        private Instant lastEvaluatedAt;
        private Long evaluationDurationMs;
        private boolean scheduleEnabled;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CountPreview {
        private String segmentId;
        private long count;
        private long evaluationMs;
        private ExecutionPlanSummary executionPlan;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExecutionPlanPreview {
        private String segmentId;
        private ExecutionPlanSummary executionPlan;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExecutionPlanSummary {
        private String executionMode;
        private boolean bounded;
        private int conditionCount;
        private int maxDepth;
        private List<String> requiredIndexes;
        private List<String> warnings;
        private List<ExecutionPlanStep> steps;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExecutionPlanStep {
        private String path;
        private String family;
        private String field;
        private String operator;
        private String strategy;
        private boolean tenantWorkspaceScoped;
        private boolean indexedLookup;
    }

    /** Rule definition for the segmentation engine */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RuleGroup {
        private String operator; // AND, OR
        private List<RuleCondition> conditions;
        private List<RuleGroup> groups; // nested groups
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RuleCondition {
        private String field;      // subscriber field or attribute key
        private String op;         // EQUALS, CONTAINS, IN_LIST, etc.
        private Object value;      // single value or list
        private String valueType;  // STRING, NUMBER, DATE
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PredictivePreviewRequest {
        @NotNull @Size(min = 1, max = 12) private List<String> featureSources;
        private List<String> dataClassesUsed;
        private List<String> excludedDataClasses;
        @NotNull @Min(0) private Long eligibleContactCount;
        @NotNull @Min(0) private Long historicalEventCount;
        @NotNull @Min(0) private Long modeledCount;
        @NotNull @Min(0) private Long suppressionImpactCount;
        @NotNull @Min(1) @Max(365) private Integer dataFreshnessDays;
        private Boolean tenantPolicyEnabled;
        @Size(max = 128) private String policyVersion;
        private Boolean protectedDataExcluded;
        private Boolean biasDriftCheckPassed;
        @Size(max = 40) private String derivationMode;
        @Size(max = 128) private String approvalStatus;
        @Size(max = 255) private String approvedBy;
        @Size(max = 64) private String approvedAt;
        @Size(max = 128) private String rollbackSnapshotId;
        private List<String> reasonCodes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PredictivePreviewResponse {
        private boolean valid;
        private boolean applyAllowed;
        private boolean approvalRequired;
        private String derivationMode;
        private String confidenceBand;
        private String riskBand;
        private String policyVersion;
        private List<String> featureSources;
        private List<String> dataClassesUsed;
        private List<String> excludedDataClasses;
        private long eligibleContactCount;
        private long historicalEventCount;
        private int dataFreshnessDays;
        private long previewCount;
        private long suppressionImpactCount;
        private long netEligibleCount;
        private String approvalStatus;
        private String rollbackSnapshotId;
        private String rollbackSnapshotStatus;
        private List<String> reasonCodes;
        private List<String> blockedReasons;
        private List<String> warnings;
    }
}
