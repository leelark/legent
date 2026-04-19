package com.legent.audience.dto;

import java.util.List;

import java.util.Map;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataExtensionDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank @Size(max = 255) private String name;
        @Size(max = 2000) private String description;
        private boolean sendable;
        private String sendableField;
        private String primaryKeyField;
        @NotNull @Size(min = 1) private List<FieldDefinition> fields;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FieldDefinition {
        @NotBlank @Size(max = 128) private String fieldName;
        private String fieldType;
        private boolean required;
        private boolean primaryKey;
        private String defaultValue;
        private Integer maxLength;
        private int ordinal;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecordRequest {
        @NotNull private Map<String, Object> data;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private String id;
        private String name;
        private String description;
        private boolean sendable;
        private String sendableField;
        private String primaryKeyField;
        private long recordCount;
        private List<FieldDefinition> fields;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecordResponse {
        private String id;
        private String dataExtensionId;
        private Map<String, Object> data;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
