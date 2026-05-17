package com.legent.delivery.controller;

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

class ProviderControllerRbacTest {

    @Test
    void unsafeProviderEndpointsDeclareRbac() {
        List<String> missing = Stream.of(ProviderController.class.getDeclaredMethods())
                .filter(ProviderControllerRbacTest::isUnsafeMapping)
                .filter(method -> preAuthorize(method) == null)
                .map(method -> ProviderController.class.getSimpleName() + "#" + method.getName())
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void providerReadEndpointsUseDeliveryReadPermission() {
        assertThat(preAuthorizeGrants("list", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants("health", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants("list", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(expression("list")).contains("delivery:read");
        assertThat(expression("health")).contains("delivery:read");
    }

    @Test
    void providerWriteEndpointsDenyReadOnlyRoles() {
        assertThat(preAuthorizeGrants("create", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("update", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("delete", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("testProvider", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("testProvider", Set.of("ANALYST"))).isFalse();
    }

    @Test
    void deliveryOperatorCanRunProviderWriteEndpoints() {
        assertThat(preAuthorizeGrants("create", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants("update", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants("delete", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants("testProvider", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(expression("create")).contains("delivery:write");
        assertThat(expression("testProvider")).contains("delivery:write");
    }

    private static boolean isUnsafeMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class);
    }

    private static String expression(String methodName) {
        List<Method> matches = Stream.of(ProviderController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toList();
        assertThat(matches)
                .as("%s#%s method lookup", ProviderController.class.getSimpleName(), methodName)
                .hasSize(1);
        PreAuthorize annotation = preAuthorize(matches.get(0));
        assertThat(annotation)
                .as("%s#%s @PreAuthorize", ProviderController.class.getSimpleName(), methodName)
                .isNotNull();
        return annotation.value();
    }

    private static PreAuthorize preAuthorize(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
    }

    private static boolean preAuthorizeGrants(String methodName, Set<String> roles) {
        StandardEvaluationContext context = new StandardEvaluationContext(new PrincipalRoot(new TestPrincipal(roles)));
        context.setBeanResolver(beanResolver());
        Boolean result = new SpelExpressionParser()
                .parseExpression(expression(methodName))
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
