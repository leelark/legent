package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiProviderMeteringRequest {
    private String workspaceId;
    @NotBlank
    private String contractKey;
    private String providerKey;
    private String featureKey;
    private String artifactType;
    private String artifactId;
    @NotBlank
    private String requestId;
    @NotBlank
    private String requestedAction;
    private BigDecimal unitsRequested;
    private BigDecimal costEstimate;
    private String currencyCode;
    private List<String> requestedDataClasses;
    private Boolean disclosureAccepted;
    private List<String> evidenceRefs;
    private Map<String, Object> context;
}
