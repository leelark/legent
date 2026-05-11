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
    public static class SchemaUpdateRequest {
        @NotNull @Size(min = 1) private List<FieldDefinition> fields;
        private Boolean replaceExisting;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SendableConfigRequest {
        private Boolean sendable;
        @Size(max = 128) private String sendableField;
        @Size(max = 128) private String primaryKeyField;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RetentionPolicyRequest {
        @Min(1) @Max(3650) private Integer retentionDays;
        @Pattern(regexp = "^(NONE|DELETE_RECORDS|ARCHIVE_RECORDS)$") private String retentionAction;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RelationshipDefinition {
        @NotBlank @Size(max = 128) private String name;
        @NotBlank private String targetDataExtensionId;
        @NotBlank @Size(max = 128) private String sourceField;
        @NotBlank @Size(max = 128) private String targetField;
        @Pattern(regexp = "^(ONE_TO_ONE|ONE_TO_MANY|MANY_TO_ONE|MANY_TO_MANY)$") private String cardinality;
        private Boolean required;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RelationshipRequest {
        @NotNull private List<RelationshipDefinition> relationships;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecordRequest {
        @NotNull private Map<String, Object> data;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QueryFilter {
        @NotBlank @Size(max = 128) private String fieldName;
        @NotBlank private String operator;
        private Object value;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QueryPreviewRequest {
        private List<String> fields;
        private List<QueryFilter> filters;
        @Size(max = 128) private String sortField;
        private String sortDirection;
        @Min(1) @Max(500) private Integer limit;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QueryPreviewResponse {
        private List<Map<String, Object>> rows;
        private int returnedRows;
        private long scannedRows;
        private List<String> warnings;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ImportMappingPreviewRequest {
        @NotNull private Map<String, String> fieldMapping;
        private List<String> sourceHeaders;
        private List<Map<String, Object>> sampleRows;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ImportMappingPreviewResponse {
        private boolean valid;
        private Map<String, String> normalizedMapping;
        private List<Map<String, Object>> sampleMappedRows;
        private List<String> errors;
        private List<String> warnings;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private String id;
        private String name;
        private String description;
        private boolean sendable;
        private String sendableField;
        private String primaryKeyField;
        private Integer retentionDays;
        private String retentionAction;
        private List<RelationshipDefinition> relationships;
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
