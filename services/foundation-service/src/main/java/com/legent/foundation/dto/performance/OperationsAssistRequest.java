package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class OperationsAssistRequest {
    private String workspaceId;
    @NotBlank
    private String operationType;
    private String artifactType;
    private String artifactId;
    private Map<String, Object> telemetry;
    private List<String> evidenceRefs;
    private Map<String, Object> constraints;
}
