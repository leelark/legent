package com.legent.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

public class AssetDto {

    @Data
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