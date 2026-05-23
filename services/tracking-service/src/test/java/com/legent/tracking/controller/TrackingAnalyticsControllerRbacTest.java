package com.legent.tracking.controller;

import com.legent.security.RbacEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingAnalyticsControllerRbacTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            AnalyticsController.class,
            BiAnalyticsController.class,
            SegmentController.class,
            FunnelController.class
    );

    @Test
    void analyticsControllersDeclareMethodRbacForEveryEndpoint() {
        List<String> missing = CONTROLLERS.stream()
                .flatMap(controller -> mappedMethods(controller)
                        .filter(method -> preAuthorize(method) == null)
                        .map(method -> controller.getSimpleName() + "#" + method.getName()))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void analyticsReadAndExportGrantAnalystViewerAndWorkspaceOwner() {
        assertThat(preAuthorizeGrants(AnalyticsController.class, "getEventCounts", Set.of("ANALYST"))).isTrue();
        assertThat(preAuthorizeGrants(AnalyticsController.class, "exportEvents", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants(BiAnalyticsController.class, "campaignPerformance", Set.of("WORKSPACE_OWNER"))).isTrue();
        assertThat(preAuthorizeGrants(SegmentController.class, "getSegment", Set.of("ANALYST"))).isTrue();
        assertThat(preAuthorizeGrants(FunnelController.class, "getFunnel", Set.of("tracking:read"))).isTrue();
        assertThat(expression(AnalyticsController.class, "exportEvents")).contains("tracking:read", "analytics:read");
    }

    @Test
    void biRollupMutationsDenyReadOnlyRoles() {
        assertThat(preAuthorizeGrants(BiAnalyticsController.class, "ensureRollups", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants(BiAnalyticsController.class, "refreshRollups", Set.of("ANALYST"))).isFalse();
        assertThat(preAuthorizeGrants(BiAnalyticsController.class, "refreshRollups", Set.of("DELIVERY_OPERATOR"))).isFalse();
    }

    @Test
    void biRollupMutationsRequireTrackingOrAnalyticsWrite() {
        assertThat(preAuthorizeGrants(BiAnalyticsController.class, "ensureRollups", Set.of("tracking:write"))).isTrue();
        assertThat(preAuthorizeGrants(BiAnalyticsController.class, "refreshRollups", Set.of("analytics:write"))).isTrue();
        assertThat(preAuthorizeGrants(BiAnalyticsController.class, "refreshRollups", Set.of("ADMIN"))).isTrue();
        assertThat(expression(BiAnalyticsController.class, "refreshRollups")).contains("tracking:write", "analytics:write");
    }

    @Test
    void conversionIngestionRequiresTrackingOrAnalyticsWrite() {
        assertThat(preAuthorizeGrants(IngestionController.class, "trackConversion", Set.of("tracking:write"))).isTrue();
        assertThat(preAuthorizeGrants(IngestionController.class, "trackConversion", Set.of("analytics:write"))).isTrue();
        assertThat(preAuthorizeGrants(IngestionController.class, "trackConversion", Set.of("ADMIN"))).isTrue();
        assertThat(preAuthorizeGrants(IngestionController.class, "trackConversion", Set.of("tracking:read"))).isFalse();
        assertThat(preAuthorizeGrants(IngestionController.class, "trackConversion", Set.of("ANALYST"))).isFalse();
        assertThat(preAuthorizeGrants(IngestionController.class, "trackConversion", Set.of("VIEWER"))).isFalse();
        assertThat(expression(IngestionController.class, "trackConversion")).contains("tracking:write", "analytics:write");
    }

    @Test
    void signedOpenAndClickEndpointsRemainPublicAtMethodLevel() {
        assertThat(preAuthorize(IngestionController.class, "trackOpen")).isNull();
        assertThat(preAuthorize(IngestionController.class, "trackClick")).isNull();
    }

    private static Stream<Method> mappedMethods(Class<?> controller) {
        return Stream.of(controller.getDeclaredMethods())
                .filter(TrackingAnalyticsControllerRbacTest::hasEndpointMapping);
    }

    private static boolean hasEndpointMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, GetMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
    }

    private static String expression(Class<?> controller, String methodName) {
        Method method = method(controller, methodName);
        PreAuthorize annotation = preAuthorize(method);
        assertThat(annotation)
                .as("%s#%s @PreAuthorize", controller.getSimpleName(), methodName)
                .isNotNull();
        return annotation.value();
    }

    private static Method method(Class<?> controller, String methodName) {
        List<Method> matches = Stream.of(controller.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .toList();
        assertThat(matches)
                .as("%s#%s method lookup", controller.getSimpleName(), methodName)
                .hasSize(1);
        return matches.get(0);
    }

    private static PreAuthorize preAuthorize(Class<?> controller, String methodName) {
        return preAuthorize(method(controller, methodName));
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
