package com.legent.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacEvaluatorTest {

    private final RbacEvaluator evaluator = new RbacEvaluator();

    @Test
    void hasPermission_whenAdmin_returnsTrueForAnyPermission() {
        assertTrue(evaluator.hasPermission("campaign:send", Set.of("ADMIN")));
        assertTrue(evaluator.hasPermission("deliverability:read", Set.of("role_admin")));
    }

    @Test
    void hasPermission_whenRoleMappedPermissionExists_returnsTrue() {
        assertTrue(evaluator.hasPermission("campaign:launch", Set.of("CAMPAIGN_MANAGER")));
        assertTrue(evaluator.hasPermission("tracking:read", Set.of("ROLE_ANALYST")));
    }

    @Test
    void hasPermission_whenWildcardRoleMatches_returnsTrue() {
        assertTrue(evaluator.hasPermission("campaign:read", Set.of("VIEWER")));
        assertFalse(evaluator.hasPermission("campaign:write", Set.of("VIEWER")));
    }

    @Test
    void hasPermission_whenDirectPermissionGrantProvided_returnsTrue() {
        assertTrue(evaluator.hasPermission("workflow:pause", Set.of("workflow:pause")));
        assertTrue(evaluator.hasPermission("webhook:delete", Set.of("webhook:*")));
    }

    @Test
    void hasPermission_whenInputInvalidOrNotGranted_returnsFalse() {
        assertFalse(evaluator.hasPermission("campaign:send", Set.of("ANALYST")));
        assertFalse(evaluator.hasPermission(" ", Set.of("ADMIN")));
        assertFalse(evaluator.hasPermission("campaign:send", Set.of()));
        assertFalse(evaluator.hasPermission("campaign:send", null));
    }
}
