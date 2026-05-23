package com.legent.deliverability.controller;

import com.legent.security.RbacEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DomainControllerRbacTest {

    @Test
    void authenticatedDeliverabilityControllerEndpointsDeclareRbac() {
        List<String> missing = Stream.of(
                        DomainController.class,
                        SuppressionController.class,
                        DmarcController.class,
                        ReputationController.class)
                .flatMap(controller -> Stream.of(controller.getDeclaredMethods())
                        .filter(DomainControllerRbacTest::isMappedEndpoint)
                        .filter(method -> !isInternalSuppressionEndpoint(controller, method))
                        .filter(method -> preAuthorize(method) == null)
                        .map(method -> controller.getSimpleName() + "#" + method.getName()))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void unsafeDomainEndpointsDeclareRbac() {
        List<String> missing = Stream.of(DomainController.class.getDeclaredMethods())
                .filter(DomainControllerRbacTest::isUnsafeMapping)
                .filter(method -> preAuthorize(method) == null)
                .map(method -> DomainController.class.getSimpleName() + "#" + method.getName())
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void domainReadEndpointUsesDeliverabilityReadPermission() {
        assertThat(preAuthorizeGrants("listDomains", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants("listDomains", Set.of("ANALYST"))).isTrue();
        assertThat(preAuthorizeGrants("listDomains", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(expression("listDomains")).contains("deliverability:read");
    }

    @Test
    void insightsAuthChecksRequireDeliverabilityReadPermission() {
        assertThat(expression(DeliverabilityInsightsController.class, "authChecks"))
                .contains("deliverability:read");
        assertThat(preAuthorizeGrants(DeliverabilityInsightsController.class, "authChecks", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants(DeliverabilityInsightsController.class, "authChecks", Set.of("CAMPAIGN_MANAGER"))).isFalse();
    }

    @Test
    void suppressionReadEndpointsUseDeliverabilityReadPermission() {
        assertThat(expression(SuppressionController.class, "listSuppressions")).contains("deliverability:read");
        assertThat(expression(SuppressionController.class, "suppressionHistory")).contains("deliverability:read");
        assertThat(preAuthorizeGrants(SuppressionController.class, "listSuppressions", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants(SuppressionController.class, "suppressionHistory", Set.of("ANALYST"))).isTrue();
        assertThat(preAuthorizeGrants(SuppressionController.class, "listSuppressions", Set.of("CAMPAIGN_MANAGER"))).isFalse();
    }

    @Test
    void internalSuppressionEndpointsKeepServiceCredentialGuard() throws NoSuchMethodException {
        assertThat(preAuthorize(SuppressionController.class.getDeclaredMethod(
                "listSuppressionsInternal",
                String.class,
                Integer.class)))
                .isNull();
        assertThat(preAuthorize(SuppressionController.class.getDeclaredMethod(
                "checkSuppressionsInternal",
                String.class,
                SuppressionController.SuppressionCheckRequest.class)))
                .isNull();
    }

    @Test
    void dmarcReportsReadAndIngestUseSeparatePermissions() {
        assertThat(expression(DmarcController.class, "getReports")).contains("deliverability:read");
        assertThat(expression(DmarcController.class, "ingest")).contains("deliverability:write");
        assertThat(preAuthorizeGrants(DmarcController.class, "getReports", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants(DmarcController.class, "ingest", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants(DmarcController.class, "ingest", Set.of("ANALYST"))).isFalse();
        assertThat(preAuthorizeGrants(DmarcController.class, "ingest", Set.of("DELIVERY_OPERATOR"))).isTrue();
    }

    @Test
    void reputationReadEndpointUsesDeliverabilityReadPermission() {
        assertThat(expression(ReputationController.class, "getScoreByDomain")).contains("deliverability:read");
        assertThat(preAuthorizeGrants(ReputationController.class, "getScoreByDomain", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants(ReputationController.class, "getScoreByDomain", Set.of("CAMPAIGN_MANAGER"))).isFalse();
    }

    @Test
    void domainWriteEndpointsDenyReadOnlyRoles() {
        assertThat(preAuthorizeGrants("registerDomain", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("regenerateChallenge", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("verifyDomain", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants("registerDomain", Set.of("ANALYST"))).isFalse();
    }

    @Test
    void deliveryOperatorCanRunDomainWriteEndpoints() {
        assertThat(preAuthorizeGrants("registerDomain", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants("regenerateChallenge", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(preAuthorizeGrants("verifyDomain", Set.of("DELIVERY_OPERATOR"))).isTrue();
        assertThat(expression("registerDomain")).contains("deliverability:write");
        assertThat(expression("regenerateChallenge")).contains("deliverability:write");
        assertThat(expression("verifyDomain")).contains("deliverability:write");
    }

    private static boolean isUnsafeMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class);
    }

    private static boolean isMappedEndpoint(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, GetMapping.class)
                || isUnsafeMapping(method);
    }

    private static boolean isInternalSuppressionEndpoint(Class<?> controller, Method method) {
        return controller.equals(SuppressionController.class)
                && Set.of("listSuppressionsInternal", "checkSuppressionsInternal").contains(method.getName());
    }

    private static String expression(String methodName) {
        return expression(DomainController.class, methodName);
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

    private static boolean preAuthorizeGrants(String methodName, Set<String> roles) {
        return preAuthorizeGrants(DomainController.class, methodName, roles);
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
