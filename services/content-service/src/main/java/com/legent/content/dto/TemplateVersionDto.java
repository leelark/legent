package com.legent.content.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

public class TemplateVersionDto {

    @Data
    public static class Create {
        @Size(max = 500)
        private String subject;

        private String htmlContent;
        private String textContent;
        private String changes;
        private Boolean publish = false;
    }

    @Data
    public static class Response {
        private String id;
        private Integer versionNumber;
        private String subject;
        private String htmlContent;
        private String textContent;
        private String changes;
        private Boolean isPublished;
        private String createdAt;
        private String updatedAt;
    }
}