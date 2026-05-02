package com.legent.foundation.dto;

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
 * DTO for tenant operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be at most 255 characters")
        private String name;

        @NotBlank(message = "Slug is required")
        @Size(max = 128, message = "Slug must be at most 128 characters")
        private String slug;

        private String plan;
        private Map<String, Object> settings;
        private Map<String, Object> branding;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 255, message = "Name must be at most 255 characters")
        private String name;
        private String plan;
        private String status;
        private Map<String, Object> settings;
        private Map<String, Object> branding;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String id;
        private String name;
        private String slug;
        private String status;
        private String plan;
        private Map<String, Object> settings;
        private Map<String, Object> branding;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant suspendedAt;
        private String suspensionReason;
        private Instant archivedAt;
    }
}
