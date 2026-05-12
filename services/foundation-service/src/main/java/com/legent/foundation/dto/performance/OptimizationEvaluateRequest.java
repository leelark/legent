package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class OptimizationEvaluateRequest {
    private String workspaceId;
    @NotBlank
    private String policyKey;
    private String artifactType;
    private String artifactId;
    private Map<String, Object> signals;
}
