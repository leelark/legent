package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class PersonalizationEvaluateRequest {
    private String workspaceId;
    private String region;
    private String evaluationKey;
    private String subjectId;
    private String eventType;
    private Map<String, Object> profile;
    private Map<String, Object> event;
    private List<Map<String, Object>> segmentRules;
    private List<Map<String, Object>> variants;
    private Map<String, Object> guardrails;
    private Map<String, Object> personalizationDefaults;
    @Min(0)
    private Integer simulatedLatencyMs;
    private Map<String, Object> metadata;
}
