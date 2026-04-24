package com.legent.content.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public class EmailDto {

    @Data
    public static class Create {
        @NotBlank
        private String name;

        @NotBlank
        private String subject;

        @NotBlank
        private String body;

        private String templateId;
    }

    @Data
    public static class Response {
        private String id;
        private String name;
        private String subject;
        private String body;
        private String templateId;
        private String status;
        private String createdAt;
    }
}
