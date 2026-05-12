package com.legent.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class FederationDto {

    @Getter
    @Setter
    public static class ProviderRequest {
        @NotBlank
        private String providerKey;
        @NotBlank
        private String displayName;
        @NotBlank
        private String protocol;
        private String status;
        private String issuer;
        private String clientId;
        private String clientSecretRef;
        private String authorizationEndpoint;
        private String tokenEndpoint;
        private String userInfoEndpoint;
        private String jwksUrl;
        private String redirectUri;
        private List<String> scopes;
        private String entityId;
        private String ssoUrl;
        private String audience;
        private String signingCertificate;
        private Boolean jitProvisioningEnabled;
        private Boolean scimEnabled;
        private String defaultWorkspaceId;
        private List<String> defaultRoleKeys;
        private Map<String, Object> attributeMapping;
        private Map<String, Object> metadata;
    }

    @Getter
    @Setter
    public static class ScimTokenRequest {
        @NotBlank
        private String providerId;
        @NotBlank
        private String label;
        private List<String> scopes;
        private Instant expiresAt;
    }

    @Getter
    @Setter
    public static class SamlAcsRequest {
        @NotBlank
        private String samlResponse;
        private String relayState;
    }

    @Getter
    @Setter
    public static class ScimUserRequest {
        private List<String> schemas;
        private String id;
        private String externalId;
        @NotBlank
        @Email
        private String userName;
        private Map<String, Object> name;
        private String displayName;
        private Boolean active;
        private List<Map<String, Object>> emails;
        private List<Map<String, Object>> groups;
        private Map<String, Object> enterpriseUser;
    }

    @Getter
    @Setter
    public static class ScimPatchRequest {
        private List<String> schemas;
        private List<ScimPatchOperation> operations;
    }

    @Getter
    @Setter
    public static class ScimPatchOperation {
        private String op;
        private String path;
        private Object value;
    }

    @Getter
    @Setter
    public static class ScimGroupRequest {
        private List<String> schemas;
        private String id;
        private String externalId;
        @NotBlank
        private String displayName;
        private List<Map<String, Object>> members;
    }
}
