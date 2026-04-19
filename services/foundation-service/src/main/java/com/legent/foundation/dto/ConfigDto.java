package com.legent.foundation.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * DTO for system configuration operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConfigDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Config key is required")
        @Size(max = 128, message = "Key must be at most 128 characters")
        private String configKey;

        @NotBlank(message = "Config value is required")
        private String configValue;

        private String valueType;
        private String category;

        @Size(max = 500)
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @NotBlank(message = "Config value is required")
        private String configValue;

        @Size(max = 500)
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String id;
        private String tenantId;
        private String configKey;
        private String configValue;
        private String valueType;
        private String category;
        private String description;
        private boolean encrypted;
        private boolean system;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
