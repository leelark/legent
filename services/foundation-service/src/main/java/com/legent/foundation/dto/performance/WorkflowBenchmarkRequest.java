package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class WorkflowBenchmarkRequest {
    private String workspaceId;
    @NotBlank
    private String benchmarkKey;
    @NotBlank
    private String flowName;
    private String competitor;
    @Min(0)
    private Integer campaignCreationSeconds;
    @Min(0)
    private Integer launchErrors;
    @Min(0)
    @Max(100)
    private Integer observabilityScore;
    @Min(0)
    private Integer competitorCreationSeconds;
    @Min(0)
    private Integer competitorLaunchErrors;
    @Min(0)
    @Max(100)
    private Integer competitorObservabilityScore;
    private Map<String, Object> evidence;
}
