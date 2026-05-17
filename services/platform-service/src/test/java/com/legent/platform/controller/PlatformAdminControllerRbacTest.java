package com.legent.platform.controller;

import com.legent.security.RbacEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminControllerRbacTest {

    @Test
    void adminAliasControllersKeepRbac() {
        assertThat(classExpression(WebhookController.class)).contains("ADMIN", "PLATFORM_ADMIN", "ORG_ADMIN");
        assertThat(classExpression(PlatformConfigController.class)).contains("config:*", "platform:*");
        assertThat(methodExpression(SearchController.class, "adminSearch")).contains("ADMIN", "PLATFORM_ADMIN");
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
        assertThat(getMappingValues(SearchController.class, "adminSearch")).containsExactly("/api/v1/admin/search");
        assertThat(methodPreAuthorizeGrants(SearchController.class, "adminSearch", Set.of("VIEWER"))).isFalse();
        assertThat(methodPreAuthorizeGrants(SearchController.class, "adminSearch", Set.of("ORG_ADMIN"))).isFalse();
        assertThat(methodPreAuthorizeGrants(SearchController.class, "adminSearch", Set.of("PLATFORM_ADMIN"))).isTrue();
    }

    @Test
    void workspaceSearchAllowsReadPermissionWithoutAdminAliasAccess() {
        assertThat(getMappingValues(SearchController.class, "search")).containsExactly("/api/v1/platform/search");
        assertThat(methodPreAuthorizeGrants(SearchController.class, "search", Set.of("VIEWER"))).isTrue();
        assertThat(methodPreAuthorizeGrants(SearchController.class, "search", Set.of("WORKSPACE_OWNER"))).isTrue();
        assertThat(methodPreAuthorizeGrants(SearchController.class, "search", Set.of("search:read"))).isTrue();
        assertThat(methodPreAuthorizeGrants(SearchController.class, "search", Set.of("CAMPAIGN_MANAGER"))).isFalse();
        assertThat(methodPreAuthorizeGrants(SearchController.class, "adminSearch", Set.of("search:read"))).isFalse();
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

    private static String methodExpression(Class<?> controller, String methodName) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method(controller, methodName), PreAuthorize.class);
        assertThat(annotation)
                .as("%s.%s @PreAuthorize", controller.getSimpleName(), methodName)
                .isNotNull();
        return annotation.value();
    }

    private static boolean classPreAuthorizeGrants(Class<?> controller, Set<String> roles) {
        return expressionGrants(classExpression(controller), roles);
    }

    private static boolean methodPreAuthorizeGrants(Class<?> controller, String methodName, Set<String> roles) {
        return expressionGrants(methodExpression(controller, methodName), roles);
    }

    private static boolean expressionGrants(String expression, Set<String> roles) {
        StandardEvaluationContext context = new StandardEvaluationContext(new PrincipalRoot(new TestPrincipal(roles)));
        context.setBeanResolver(beanResolver());
        Boolean result = new SpelExpressionParser()
                .parseExpression(expression)
                .getValue(context, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    private static Method method(Class<?> controller, String methodName) {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method: " + controller.getSimpleName() + "." + methodName));
    }

    private static String[] getMappingValues(Class<?> controller, String methodName) {
        GetMapping annotation = AnnotatedElementUtils.findMergedAnnotation(method(controller, methodName), GetMapping.class);
        assertThat(annotation)
                .as("%s.%s @GetMapping", controller.getSimpleName(), methodName)
                .isNotNull();
        return annotation.value();
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
