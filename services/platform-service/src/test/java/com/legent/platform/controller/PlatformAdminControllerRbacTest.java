package com.legent.platform.controller;

import com.legent.security.RbacEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminControllerRbacTest {

    @Test
    void adminAliasControllersKeepClassLevelRbac() {
        assertThat(classExpression(WebhookController.class)).contains("ADMIN", "PLATFORM_ADMIN", "ORG_ADMIN");
        assertThat(classExpression(SearchController.class)).contains("ADMIN", "PLATFORM_ADMIN");
        assertThat(classExpression(PlatformConfigController.class)).contains("config:*", "platform:*");
    }

    @Test
    void webhookAdminAliasDeniesViewerAndAnalyst() {
        assertThat(classPreAuthorizeGrants(WebhookController.class, Set.of("VIEWER"))).isFalse();
        assertThat(classPreAuthorizeGrants(WebhookController.class, Set.of("ANALYST"))).isFalse();
        assertThat(classPreAuthorizeGrants(WebhookController.class, Set.of("ORG_ADMIN"))).isTrue();
        assertThat(classPreAuthorizeGrants(WebhookController.class, Set.of("PLATFORM_ADMIN"))).isTrue();
    }

    @Test
    void searchAdminAliasRequiresPlatformAdmin() {
        assertThat(classPreAuthorizeGrants(SearchController.class, Set.of("VIEWER"))).isFalse();
        assertThat(classPreAuthorizeGrants(SearchController.class, Set.of("ORG_ADMIN"))).isFalse();
        assertThat(classPreAuthorizeGrants(SearchController.class, Set.of("PLATFORM_ADMIN"))).isTrue();
    }

    @Test
    void platformConfigUsesPermissionEvaluator() {
        assertThat(classPreAuthorizeGrants(PlatformConfigController.class, Set.of("VIEWER"))).isFalse();
        assertThat(classPreAuthorizeGrants(PlatformConfigController.class, Set.of("config:*"))).isTrue();
        assertThat(classPreAuthorizeGrants(PlatformConfigController.class, Set.of("PLATFORM_ADMIN"))).isTrue();
    }

    private static String classExpression(Class<?> controller) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(controller, PreAuthorize.class);
        assertThat(annotation)
                .as("%s @PreAuthorize", controller.getSimpleName())
                .isNotNull();
        return annotation.value();
    }

    private static boolean classPreAuthorizeGrants(Class<?> controller, Set<String> roles) {
        StandardEvaluationContext context = new StandardEvaluationContext(new PrincipalRoot(new TestPrincipal(roles)));
        context.setBeanResolver(beanResolver());
        Boolean result = new SpelExpressionParser()
                .parseExpression(classExpression(controller))
                .getValue(context, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    private static BeanResolver beanResolver() {
        return (context, beanName) -> {
            if ("rbacEvaluator".equals(beanName)) {
                return new RbacEvaluator();
            }
            throw new IllegalArgumentException("Unexpected bean lookup: " + beanName);
        };
    }

    private record PrincipalRoot(TestPrincipal principal) {
        @SuppressWarnings("unused")
        public boolean hasAnyRole(String... requiredRoles) {
            Set<String> normalized = principal.roles().stream()
                    .map(PrincipalRoot::normalizeRole)
                    .collect(java.util.stream.Collectors.toSet());
            return Arrays.stream(requiredRoles)
                    .map(PrincipalRoot::normalizeRole)
                    .anyMatch(normalized::contains);
        }

        private static String normalizeRole(String role) {
            String normalized = role == null ? "" : role.trim();
            if (normalized.regionMatches(true, 0, "ROLE_", 0, 5)) {
                normalized = normalized.substring(5);
            }
            return normalized.toUpperCase(Locale.ROOT);
        }
    }

    private record TestPrincipal(Set<String> roles) {
    }
}
