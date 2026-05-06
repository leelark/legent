package com.legent.identity.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginResponse {
    private String status;
    private String userId;
    private String tenantId;
    private java.util.List<String> roles;
    private String workspaceId;
    private String environmentId;

    public LoginResponse(String status, String userId, String tenantId, java.util.List<String> roles) {
        this.status = status;
        this.userId = userId;
        this.tenantId = tenantId;
        this.roles = roles;
    }

    public LoginResponse(String status, String userId, String tenantId, java.util.List<String> roles, String workspaceId, String environmentId) {
        this.status = status;
        this.userId = userId;
        this.tenantId = tenantId;
        this.roles = roles;
        this.workspaceId = workspaceId;
        this.environmentId = environmentId;
    }
}
