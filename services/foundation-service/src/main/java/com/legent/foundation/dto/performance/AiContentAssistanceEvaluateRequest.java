package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiContentAssistanceEvaluateRequest {
    private String workspaceId;
    @NotBlank
    private String policyKey;
    private String artifactType;
    private String artifactId;
    @NotBlank
    private String requestedAction;
    private String promptTemplateVersion;
    private String promptHash;
    private String promptText;
    private String outputHash;
    private String outputText;
    private List<String> requestedDataClasses;
    private Boolean humanReviewApproved;
    private Map<String, Object> reviewDecision;
    private Map<String, Object> context;
    private List<String> evidenceRefs;
}
