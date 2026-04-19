package com.legent.audience.dto;

import java.util.Map;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImportDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StartRequest {
        @NotBlank private String fileName;
        private Long fileSize;
        private String targetType; // SUBSCRIBER, DATA_EXTENSION
        private String targetId;
        @NotNull private Map<String, String> fieldMapping;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatusResponse {
        private String id;
        private String fileName;
        private String status;
        private String targetType;
        private long totalRows;
        private long processedRows;
        private long successRows;
        private long errorRows;
        private double progressPercent;
        private Instant startedAt;
        private Instant completedAt;
        private Instant createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ErrorDetail {
        private int rowNumber;
        private String field;
        private String value;
        private String message;
    }
}
