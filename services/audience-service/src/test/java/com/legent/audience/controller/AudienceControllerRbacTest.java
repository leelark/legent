package com.legent.audience.controller;

import com.legent.security.RbacEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceControllerRbacTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            DataExtensionController.class,
            ImportController.class,
            LocalImportController.class,
            PreferenceController.class,
            SegmentController.class,
            SendEligibilityController.class,
            SubscriberController.class,
            SubscriberListController.class,
            SuppressionController.class
    );

    @Test
    void unsafeAudienceControllerEndpointsDeclareRbac() {
        List<String> missing = CONTROLLERS.stream()
                .flatMap(controller -> Stream.of(controller.getDeclaredMethods())
                        .filter(AudienceControllerRbacTest::isUnsafeMapping)
                        .filter(method -> preAuthorize(method) == null)
                        .map(method -> controller.getSimpleName() + "#" + method.getName()))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void audienceReadEndpointsGrantViewerAndAnalystRoles() {
        assertThat(preAuthorizeGrants(SubscriberController.class, "list", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants(SubscriberController.class, "list", Set.of("ANALYST"))).isTrue();
        assertThat(preAuthorizeGrants(SendEligibilityController.class, "check", Set.of("VIEWER"))).isTrue();
        assertThat(expression(DataExtensionController.class, "previewQuery")).contains("audience:read");
        assertThat(expression(DataExtensionController.class, "previewImportMapping")).contains("audience:read");
    }

    @Test
    void audienceWriteAndDeleteEndpointsDenyViewers() {
        assertThat(preAuthorizeGrants(SubscriberController.class, "create", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants(SubscriberController.class, "update", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants(SubscriberController.class, "delete", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants(ImportController.class, "uploadImport", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants(PreferenceController.class, "resubscribe", Set.of("VIEWER"))).isFalse();
    }

    @Test
    void audienceWildcardRolesGrantWriteAndDeleteEndpoints() {
        assertThat(preAuthorizeGrants(SubscriberController.class, "create", Set.of("CAMPAIGN_MANAGER"))).isTrue();
        assertThat(preAuthorizeGrants(SubscriberController.class, "delete", Set.of("CAMPAIGN_MANAGER"))).isTrue();
        assertThat(preAuthorizeGrants(DataExtensionController.class, "addRecord", Set.of("WORKSPACE_OWNER"))).isTrue();
        assertThat(preAuthorizeGrants(ImportController.class, "cancelImport", Set.of("WORKSPACE_OWNER"))).isTrue();
    }

    @Test
    void deleteEndpointsUseDeletePermissionWhereResourcesAreRemoved() {
        assertThat(expression(SubscriberController.class, "delete")).contains("audience:delete");
        assertThat(expression(SubscriberListController.class, "delete")).contains("audience:delete");
        assertThat(expression(SegmentController.class, "delete")).contains("audience:delete");
        assertThat(expression(SuppressionController.class, "delete")).contains("audience:delete");
        assertThat(expression(DataExtensionController.class, "delete")).contains("audience:delete");
    }

    private static boolean isUnsafeMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class);
    }

    private static String expression(Class<?> controller, String methodName) {
        List<Method> matches = Stream.of(controller.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toList();
        assertThat(matches)
                .as("%s#%s method lookup", controller.getSimpleName(), methodName)
                .hasSize(1);
        PreAuthorize annotation = preAuthorize(matches.get(0));
        assertThat(annotation)
                .as("%s#%s @PreAuthorize", controller.getSimpleName(), methodName)
                .isNotNull();
        return annotation.value();
    }

    private static PreAuthorize preAuthorize(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
    }

    private static boolean preAuthorizeGrants(Class<?> controller, String methodName, Set<String> roles) {
        StandardEvaluationContext context = new StandardEvaluationContext(new PrincipalRoot(new TestPrincipal(roles)));
        context.setBeanResolver(beanResolver());
        Boolean result = new SpelExpressionParser()
                .parseExpression(expression(controller, methodName))
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
    }

    private record TestPrincipal(Set<String> roles) {
    }
}
