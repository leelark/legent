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
    private Set<String> roles;
}
