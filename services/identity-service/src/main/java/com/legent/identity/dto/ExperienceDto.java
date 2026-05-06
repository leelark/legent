package com.legent.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

public class ExperienceDto {

    @Getter
    @Setter
    public static class ForgotPasswordRequest {
        @NotBlank
        @Email
        private String email;
    }

    @Getter
    @Setter
    public static class ResetPasswordRequest {
        @NotBlank
        private String token;
        @NotBlank
        private String newPassword;
    }

    @Getter
    @Setter
    public static class OnboardingStartRequest {
        private String workspaceId;
        private String stepKey;
        private Map<String, Object> payload;
    }

    @Getter
    @Setter
    public static class OnboardingCompleteRequest {
        private String workspaceId;
        private Map<String, Object> payload;
    }

    @Getter
    @Setter
    public static class UserPreferenceRequest {
        private String uiMode;
        private String theme;
        private String density;
        private Boolean sidebarCollapsed;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class UserPreferenceResponse {
        private String tenantId;
        private String userId;
        private String uiMode;
        private String theme;
        private String density;
        private boolean sidebarCollapsed;
        private Map<String, Object> metadata;
    }
}

