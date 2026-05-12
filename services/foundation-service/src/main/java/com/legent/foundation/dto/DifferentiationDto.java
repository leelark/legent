package com.legent.foundation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DifferentiationDto {

    @Getter
    @Setter
    public static class CopilotRecommendationRequest {
        private String workspaceId;
        @NotBlank
        private String artifactType;
        private String artifactId;
        @NotBlank
        private String objective;
        private String audienceSummary;
        private String riskTolerance;
        private Boolean requireHumanApproval;
        private Map<String, Object> policyContext;
        private Map<String, Object> candidateContent;
        private List<String> constraints;
    }

    @Getter
    @Setter
    public static class CopilotDecisionRequest {
        @NotBlank
        private String decision;
        private String decisionNote;
    }

    @Getter
    @Setter
    public static class DecisionPolicyRequest {
        private String workspaceId;
        @NotBlank
        private String policyKey;
        @NotBlank
        private String name;
        private String status;
        private String triggerEvent;
        private String channel;
        private Map<String, Object> rules;
        private List<Map<String, Object>> variants;
        private Map<String, Object> guardrails;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class DecisionEvaluateRequest {
        @NotBlank
        private String policyKey;
        private String eventType;
        private String channel;
        private Map<String, Object> profileUpdates;
        private Map<String, Object> context;
    }

    @Getter
    @Setter
    public static class OmnichannelFlowRequest {
        private String workspaceId;
        @NotBlank
        private String flowKey;
        @NotBlank
        private String name;
        private String status;
        private Boolean transactional;
        private List<String> channels;
        private Map<String, Object> routingRules;
        private Map<String, Object> guardrails;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class OmnichannelSimulationRequest {
        @NotBlank
        private String flowKey;
        private List<String> preferredChannels;
        private Map<String, Object> recipient;
        private Map<String, Object> event;
    }

    @Getter
    @Setter
    public static class DeveloperPackageRequest {
        @NotBlank
        private String appKey;
        @NotBlank
        private String displayName;
        private String status;
        private String apiVersion;
        private List<String> scopes;
        private List<String> sdkTargets;
        private Boolean sandboxEnabled;
        private String marketplaceStatus;
        private Boolean webhookReplayEnabled;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class SandboxRequest {
        @NotBlank
        private String appPackageId;
        private String dataProfile;
        private Map<String, Object> seedOptions;
    }

    @Getter
    @Setter
    public static class WebhookReplayRequest {
        @NotBlank
        private String appPackageId;
        private String sourceWebhookId;
        private String targetUrl;
        private Boolean dryRun;
        private Instant fromTime;
        private Instant toTime;
        private List<String> eventTypes;
    }

    @Getter
    @Setter
    public static class SloPolicyRequest {
        private String workspaceId;
        @NotBlank
        private String serviceName;
        private String status;
        @Min(0)
        @Max(100)
        private Double sloTargetPercent;
        private String window;
        @Min(0)
        private Double errorBudgetMinutes;
        private Map<String, Object> syntheticProbe;
        private List<Map<String, Object>> selfHealingActions;
        private Map<String, Object> capacityForecast;
        private Map<String, Object> incidentAutomation;
    }

    @Getter
    @Setter
    public static class SloEvaluateRequest {
        @NotBlank
        private String serviceName;
        @Min(0)
        @Max(100)
        private Double successRatePercent;
        @Min(0)
        private Long p95LatencyMs;
        @Min(0)
        @Max(100)
        private Double saturationPercent;
        @Min(0)
        private Long queueDepth;
        @Min(0)
        private Long requests;
        @Min(0)
        private Long errors;
    }
}
