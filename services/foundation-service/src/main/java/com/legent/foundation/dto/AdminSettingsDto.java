package com.legent.foundation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminSettingsDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        private String id;
        private String key;
        private String module;
        private String category;
        private String type;
        private String scope;
        private String tenantId;
        private String workspaceId;
        private String environmentId;
        private String value;
        private Integer version;
        private String updatedBy;
        private Instant updatedAt;
        private List<String> dependencyKeys;
        private String validationSchema;
        private String metadata;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplyRequest {
        @NotBlank
        private String key;
        @NotBlank
        private String value;
        private String module;
        private String category;
        private String type;
        private String scope;
        private String workspaceId;
        private String environmentId;
        private List<String> dependencyKeys;
        private Map<String, Object> validationSchema;
        private Map<String, Object> metadata;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidateResponse {
        private boolean valid;
        private List<String> errors;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetRequest {
        @NotBlank
        private String key;
        private String scope;
        private String workspaceId;
        private String environmentId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactResponse {
        private String key;
        private String module;
        private List<String> impactedModules;
        private List<String> notices;
    }
}
