package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiProviderContractRequest {
    private String workspaceId;
    @NotBlank
    private String contractKey;
    @NotBlank
    private String providerKey;
    @NotBlank
    private String providerName;
    @NotBlank
    private String modelName;
    private String status;
    private String featureClass;
    private String deploymentRegion;
    private String processor;
    private Map<String, Object> providerDisclosure;
    private List<String> allowedDataClasses;
    private List<String> prohibitedDataClasses;
    private Boolean trainingUsageAllowed;
    private Boolean optInRequired;
    private Boolean optOutEnabled;
    private Boolean requireHumanReview;
    private Boolean meteringEnabled;
    private Boolean killSwitchEnabled;
    private String promptStoragePolicy;
    private String outputStoragePolicy;
    private BigDecimal maxUnitsPerRequest;
    private BigDecimal monthlyUnitLimit;
    private Map<String, Object> costPolicy;
    private Map<String, Object> retentionPolicy;
    private String versionLabel;
    private Map<String, Object> metadata;
}
