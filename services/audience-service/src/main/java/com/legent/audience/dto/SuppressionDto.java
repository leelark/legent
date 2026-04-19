package com.legent.audience.dto;

import java.util.List;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuppressionDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank @Email @Size(max = 320) private String email;
        @NotBlank private String suppressionType;
        @Size(max = 500) private String reason;
        private String source;
        private Instant expiresAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BulkRequest {
        @NotNull @Size(min = 1, max = 10000)
        private List<CreateRequest> suppressions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private String id;
        private String email;
        private String suppressionType;
        private String reason;
        private String source;
        private Instant suppressedAt;
        private Instant expiresAt;
        private Instant createdAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ComplianceCheck {
        private String email;
        private boolean suppressed;
        private String suppressionType;
        private String reason;
    }
}
