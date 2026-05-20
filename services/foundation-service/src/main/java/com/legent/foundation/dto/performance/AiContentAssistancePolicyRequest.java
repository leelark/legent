package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiContentAssistancePolicyRequest {
    private String workspaceId;
    @NotBlank
    private String policyKey;
    @NotBlank
    private String name;
    private String status;
    private String featureClass;
    @NotBlank
    private String providerName;
    private String modelName;
    private String deploymentRegion;
    private String processor;
    private Map<String, Object> providerDisclosure;
    private List<String> allowedDataClasses;
    private List<String> prohibitedDataClasses;
    private Boolean trainingUsageAllowed;
    private Map<String, Object> retentionPolicy;
    private String promptStoragePolicy;
    private String outputStoragePolicy;
    private Boolean optInRequired;
    private Boolean optOutEnabled;
    private Boolean requireHumanReview;
    private Boolean draftOnly;
    private Boolean killSwitchEnabled;
    private String versionLabel;
    private Map<String, Object> metadata;
}
