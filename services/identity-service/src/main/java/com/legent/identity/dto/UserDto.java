package com.legent.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;

public class UserDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank @Email
        private String email;
        
        @NotBlank
        private String password;
        
        private String firstName;
        private String lastName;
        
        @NotBlank
        private String role;
        
        @Builder.Default
        private boolean isActive = true;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String id;
        private String tenantId;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
        private boolean isActive;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
