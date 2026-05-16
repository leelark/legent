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

class DeliveryOperationsControllerRbacTest {

    @Test
    void unsafeDeliveryOperationsDeclareRbac() {
        List<String> missing = Stream.of(DeliveryOperationsController.class.getDeclaredMethods())
                .filter(DeliveryOperationsControllerRbacTest::isUnsafeMapping)
                .filter(method -> preAuthorize(method) == null)
                .map(method -> DeliveryOperationsController.class.getSimpleName() + "#" + method.getName())
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void deliveryReadEndpointsGrantViewerAndDeliveryOperatorRoles() {
        assertThat(preAuthorizeGrants("queueStats", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants("messages", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants("deadLetters", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants("queueStats", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(expression("queueStats")).contains("delivery:read");
        assertThat(expression("deadLetters")).contains("delivery:read");
    }

    @Test
    void mutatingDeliveryOperationsDenyViewerAndAnalystRoles() {
        assertThat(preAuthorizeGrants("retryMessage", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("evaluateSafety", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("evaluateProviderCapacity", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("replayDeadLetters", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("replayDeadLetters", Set.of("ANALYST"))).isFalse();
    }

    @Test
    void deliveryOperatorCanRunDeliveryWriteOperations() {
        assertThat(preAuthorizeGrants("retryMessage", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants("evaluateSafety", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants("evaluateProviderCapacity", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants("runFailoverTest", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants("replayDeadLetters", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(expression("replayDeadLetters")).contains("delivery:write");
    }

    private static boolean isUnsafeMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class);
    }

    private static String expression(String methodName) {
        List<Method> matches = Stream.of(DeliveryOperationsController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toList();
        assertThat(matches)
                .as("%s#%s method lookup", DeliveryOperationsController.class.getSimpleName(), methodName)
                .hasSize(1);
        PreAuthorize annotation = preAuthorize(matches.get(0));
        assertThat(annotation)
                .as("%s#%s @PreAuthorize", DeliveryOperationsController.class.getSimpleName(), methodName)
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
