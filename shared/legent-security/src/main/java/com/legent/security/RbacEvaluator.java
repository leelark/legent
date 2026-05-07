package com.legent.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RBAC evaluator for permission checks.
 * Supports role matrix lookups, wildcard permissions, and direct permission grants.
 */
@Slf4j
@Component
public class RbacEvaluator {

    private static final String ADMIN = "ADMIN";

    private static final Map<String, Set<String>> ROLE_PERMISSION_MATRIX = Map.of(
            ADMIN, Set.of("*"),
            "PLATFORM_ADMIN", Set.of("platform:*", "webhook:*", "notification:*", "search:*", "tenant:*", "config:*", "audit:*", "user:*", "role:*", "admin:*"),
            "ORG_ADMIN", Set.of("tenant:*", "workspace:*", "team:*", "role:*", "user:*", "config:*", "audit:*", "feature:*"),
            "SECURITY_ADMIN", Set.of("audit:*", "user:*", "role:*", "config:read", "tenant:read", "security:*"),
            "WORKSPACE_OWNER", Set.of("workspace:*", "team:*", "campaign:*", "audience:*", "template:*", "workflow:*", "analytics:read", "tenant:read"),
            "CAMPAIGN_MANAGER", Set.of("campaign:*", "audience:*", "template:*", "workflow:*"),
            "DELIVERY_OPERATOR", Set.of("delivery:*", "deliverability:*", "tracking:read"),
            "ANALYST", Set.of("campaign:read", "audience:read", "deliverability:read", "tracking:read", "report:*"),
            "VIEWER", Set.of("*:read")
    );

    /**
     * Checks if the current user has the required permission.
     *
     * @param requiredPermission the permission to check
     * @param userRoles          the roles assigned to the current user
     * @return true if the user has the required permission
     */
    public boolean hasPermission(String requiredPermission, Set<String> userRoles) {
        String normalizedRequiredPermission = normalizePermission(requiredPermission);
        if (normalizedRequiredPermission == null) {
            log.warn("Permission check failed: required permission is blank");
            return false;
        }

        if (userRoles == null || userRoles.isEmpty()) {
            log.warn("Permission check failed: no roles assigned");
            return false;
        }

        Set<String> normalizedPrincipals = userRoles.stream()
                .map(this::normalizePrincipal)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());

        if (normalizedPrincipals.contains(ADMIN)) {
            return true;
        }

        for (String principal : normalizedPrincipals) {
            if (matchesPermissionPattern(principal, normalizedRequiredPermission)) {
                return true;
            }

            Set<String> grantedPermissions = ROLE_PERMISSION_MATRIX.get(principal);
            if (grantedPermissions == null) {
                continue;
            }

            for (String grantedPermission : grantedPermissions) {
                if (matchesPermissionPattern(grantedPermission, normalizedRequiredPermission)) {
                    return true;
                }
            }
        }

        log.debug("Permission '{}' denied for principals {}", normalizedRequiredPermission, normalizedPrincipals);
        return false;
    }

    /**
     * Quick check for admin role.
     */
    public boolean isAdmin(Set<String> userRoles) {
        return userRoles != null && userRoles.stream()
                .map(this::normalizePrincipal)
                .anyMatch(ADMIN::equals);
    }

    private String normalizePrincipal(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "ROLE_", 0, 5)) {
            normalized = normalized.substring(5);
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizePermission(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private boolean matchesPermissionPattern(String permissionPattern, String requiredPermission) {
        String normalizedPattern = normalizePermission(permissionPattern);
        if (normalizedPattern == null) {
            return false;
        }
        if ("*".equals(normalizedPattern)) {
            return true;
        }

        String regex = "^" + Pattern.quote(normalizedPattern).replace("*", "\\E.*\\Q") + "$";
        return requiredPermission.matches(regex);
    }
}
