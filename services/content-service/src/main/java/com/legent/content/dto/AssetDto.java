package com.legent.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.Map;

public class AssetDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Create {
        @NotBlank
        private String name;

        @NotBlank
        private String url;

        @NotBlank
        private String contentType;

        @NotNull
        private Long size;

        private Map<String, String> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String id;
        private String name;
        private String url;
        private String contentType;
        private Long size;
        private Map<String, String> metadata;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
