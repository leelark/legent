package com.legent.foundation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

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
}
