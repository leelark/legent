package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class OptimizationPolicyRequest {
    private String workspaceId;
    @NotBlank
    private String policyKey;
    @NotBlank
    private String name;
    @NotBlank
    private String optimizationType;
    private String status;
    private String objective;
    private String targetMetric;
    private Map<String, Object> guardrails;
    private Map<String, Object> rollbackPolicy;
    private Map<String, Object> approvalPolicy;
    private Map<String, Object> metadata;
}
