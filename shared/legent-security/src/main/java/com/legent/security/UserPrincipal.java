package com.legent.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Principal representing an authenticated user from JWT claims.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal {

    private String userId;
    private String tenantId;
    private String workspaceId;
    private String environmentId;
    private Set<String> roles;

    public UserPrincipal(String userId, String tenantId, Set<String> roles) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.roles = roles;
    }
}
