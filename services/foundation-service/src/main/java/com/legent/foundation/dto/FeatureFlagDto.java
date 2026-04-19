package com.legent.foundation.dto;

import java.util.List;

import java.util.Map;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * DTO for feature flag operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeatureFlagDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Flag key is required")
        @Size(max = 128, message = "Key must be at most 128 characters")
        private String flagKey;

        private boolean enabled;

        @Size(max = 500)
        private String description;

        private String scope;
        private List<Map<String, Object>> rules;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private Boolean enabled;

        @Size(max = 500)
        private String description;

        private List<Map<String, Object>> rules;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String id;
        private String tenantId;
        private String flagKey;
        private boolean enabled;
        private String description;
        private String scope;
        private List<Map<String, Object>> rules;
        private Map<String, Object> metadata;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationResult {
        private String flagKey;
        private boolean enabled;
        private String resolvedScope;
    }
}
