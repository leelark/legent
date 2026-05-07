package com.legent.foundation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

public class PublicContactDto {

    @Getter
    @Setter
    public static class Request {
        @Size(max = 120)
        private String name;

        @NotBlank
        @Email
        @Size(max = 255)
        private String workEmail;

        @NotBlank
        @Size(max = 160)
        private String company;

        @Size(max = 120)
        private String interest;

        @NotBlank
        @Size(max = 2000)
        private String message;

        @Size(max = 120)
        private String sourcePage;
    }

    @Getter
    @Setter
    @Builder
    public static class Response {
        private String id;
        private String status;
        private String message;
    }

    @Getter
    @Setter
    public static class StatusUpdateRequest {
        @NotBlank
        private String status;
    }

    @Getter
    @Setter
    @Builder
    public static class AdminResponse {
        private String id;
        private String name;
        private String workEmail;
        private String company;
        private String interest;
        private String message;
        private String sourcePage;
        private String status;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
