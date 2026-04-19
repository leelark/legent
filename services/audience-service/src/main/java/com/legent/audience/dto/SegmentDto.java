package com.legent.audience.dto;

import java.util.List;

import java.util.Map;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class SegmentDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank @Size(max = 255) private String name;
        @Size(max = 2000) private String description;
        private String segmentType;
        @NotNull private Map<String, Object> rules;
        private boolean scheduleEnabled;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 255) private String name;
        @Size(max = 2000) private String description;
        private Map<String, Object> rules;
        private Boolean scheduleEnabled;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private String id;
        private String name;
        private String description;
        private String status;
        private String segmentType;
        private Map<String, Object> rules;
        private long memberCount;
        private Instant lastEvaluatedAt;
        private Long evaluationDurationMs;
        private boolean scheduleEnabled;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CountPreview {
        private String segmentId;
        private long count;
        private long evaluationMs;
    }

    /** Rule definition for the segmentation engine */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RuleGroup {
        private String operator; // AND, OR, NOT
        private List<RuleCondition> conditions;
        private List<RuleGroup> groups; // nested groups
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RuleCondition {
        private String field;      // subscriber field or attribute key
        private String op;         // EQUALS, CONTAINS, IN_LIST, etc.
        private Object value;      // single value or list
        private String valueType;  // STRING, NUMBER, DATE
    }
}
