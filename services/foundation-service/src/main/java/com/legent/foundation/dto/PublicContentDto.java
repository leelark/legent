package com.legent.foundation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

public class PublicContentDto {

    @Getter
    @Setter
    public static class UpsertRequest {
        @NotBlank
        private String contentType;
        @NotBlank
        private String pageKey;
        private String slug;
        private String title;
        private String status;
        private Map<String, Object> payload;
        private Map<String, Object> seoMeta;
    }

    @Getter
    @Setter
    @Builder
    public static class Response {
        private String id;
        private String tenantId;
        private String workspaceId;
        private String contentType;
        private String pageKey;
        private String slug;
        private String title;
        private String status;
        private Map<String, Object> payload;
        private Map<String, Object> seoMeta;
        private Instant publishedAt;
        private Instant updatedAt;
    }
}

