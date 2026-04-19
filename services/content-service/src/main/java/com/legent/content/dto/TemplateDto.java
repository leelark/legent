package com.legent.content.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class TemplateDto {

    @Data
    public static class Create {
        @NotBlank
        @Size(max = 255)
        private String name;

        @Size(max = 500)
        private String subject;

        private String htmlContent;
        private String textContent;
        private String category;
        private List<String> tags;
        private String metadata;
    }

    @Data
    public static class Update {
        @Size(max = 255)
        private String name;

        @Size(max = 500)
        private String subject;

        private String htmlContent;
        private String textContent;
        private String category;
        private List<String> tags;
        private String metadata;
    }

    @Data
    public static class Response {
        private String id;
        private String name;
        private String subject;
        private String status;
        private String templateType;
        private String category;
        private List<String> tags;
        private String metadata;
        private String createdAt;
        private String updatedAt;
    }
}