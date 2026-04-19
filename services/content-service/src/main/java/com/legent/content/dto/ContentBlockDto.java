package com.legent.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class ContentBlockDto {

    @Data
    public static class Create {
        @NotBlank
        @Size(max = 255)
        private String name;

        @NotBlank
        @Size(max = 50)
        private String blockType;

        @NotBlank
        private String content;

        private String styles;
        private String settings;
        private Boolean isGlobal = false;
    }

    @Data
    public static class Response {
        private String id;
        private String name;
        private String blockType;
        private String content;
        private String styles;
        private String settings;
        private Boolean isGlobal;
    }
}