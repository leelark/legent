package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiGenerationPreviewRequest {
    private String workspaceId;
    @NotBlank
    @Size(max = 128)
    private String policyKey;
    @NotBlank
    @Size(max = 128)
    private String contractKey;
    @Size(max = 128)
    private String providerKey;
    @NotBlank
    @Size(max = 128)
    private String requestId;
    @NotBlank
    @Size(max = 1000)
    private String objective;
    @Size(max = 10)
    private List<String> generationTargets;
    @Size(max = 12)
    private List<String> requestedDataClasses;
    @Size(max = 255)
    private String segmentName;
    @Size(max = 255)
    private String workflowName;
    @Size(max = 128)
    private String campaignId;
    private Boolean humanReviewApproved;
    private Boolean disclosureAccepted;
    private Map<String, Object> segmentHints;
    private Map<String, Object> workflowHints;
    private Map<String, Object> context;
    private List<String> evidenceRefs;
}
