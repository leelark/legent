package com.legent.foundation.dto.performance;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ExtensionPackageRequest {
    private String workspaceId;
    @NotBlank
    private String packageKey;
    @NotBlank
    private String displayName;
    private String packageType;
    private String status;
    private List<String> scopes;
    private Map<String, Object> manifest;
    private List<Map<String, Object>> scripts;
    private List<String> testRequirements;
    private String approvalStatus;
    private Map<String, Object> metadata;
}
