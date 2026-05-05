package com.legent.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AuthBridgeDto {

    @Getter
    @Setter
    public static class InvitationRequest {
        @NotBlank
        @Email
        private String email;
        private String workspaceId;
        private List<String> roleKeys;
        private Instant expiresAt;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class InvitationAcceptRequest {
        @NotBlank
        private String token;
        private String firstName;
        private String lastName;
        private String password;
    }

    @Getter
    @Setter
    public static class ContextSwitchRequest {
        @NotBlank
        private String tenantId;
        private String workspaceId;
        private String environmentId;
    }

    @Getter
    @Setter
    public static class DelegationRequest {
        @NotBlank
        private String delegatedUserId;
        private String workspaceId;
        private List<String> permissions;
        private Instant expiresAt;
    }
}
