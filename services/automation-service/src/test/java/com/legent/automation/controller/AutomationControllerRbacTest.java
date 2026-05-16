package com.legent.automation.controller;

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

class AutomationControllerRbacTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            WorkflowController.class,
            WorkflowDefinitionController.class,
            AutomationStudioController.class
    );

    @Test
    void workflowControllersDeclareMethodRbacForEveryEndpoint() {
        List<String> missing = CONTROLLERS.stream()
                .flatMap(controller -> mappedMethods(controller)
                        .filter(method -> preAuthorize(method) == null)
                        .map(method -> controller.getSimpleName() + "#" + method.getName()))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void workflowReadEndpointsGrantViewerAndWorkflowRoles() {
        assertThat(preAuthorizeGrants(WorkflowController.class, "listWorkflows", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants(WorkflowController.class, "journeyAnalytics", Set.of("WORKSPACE_OWNER"))).isTrue();
        assertThat(preAuthorizeGrants(WorkflowDefinitionController.class, "getLatestDefinition", Set.of("VIEWER"))).isTrue();
        assertThat(preAuthorizeGrants(AutomationStudioController.class, "listActivities", Set.of("CAMPAIGN_MANAGER"))).isTrue();
        assertThat(expression(WorkflowController.class, "listWorkflows")).contains("workflow:read");
    }

    @Test
    void workflowMutationsDenyViewerAndAnalystRoles() {
        assertThat(preAuthorizeGrants(WorkflowController.class, "createWorkflow", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants(WorkflowController.class, "publishWorkflow", Set.of("ANALYST"))).isFalse();
        assertThat(preAuthorizeGrants(WorkflowController.class, "triggerWorkflow", Set.of("VIEWER"))).isFalse();
        assertThat(preAuthorizeGrants(WorkflowController.class, "createSchedule", Set.of("ANALYST"))).isFalse();
        assertThat(preAuthorizeGrants(AutomationStudioController.class, "runActivity", Set.of("VIEWER"))).isFalse();
    }

    @Test
    void workflowMutationRolesCanWrite() {
        assertThat(preAuthorizeGrants(WorkflowController.class, "createWorkflow", Set.of("CAMPAIGN_MANAGER"))).isTrue();
        assertThat(preAuthorizeGrants(WorkflowController.class, "publishWorkflow", Set.of("WORKSPACE_OWNER"))).isTrue();
        assertThat(preAuthorizeGrants(WorkflowController.class, "triggerWorkflow", Set.of("workflow:write"))).isTrue();
        assertThat(preAuthorizeGrants(WorkflowDefinitionController.class, "saveDefinition", Set.of("workflow:write"))).isTrue();
        assertThat(preAuthorizeGrants(AutomationStudioController.class, "createActivity", Set.of("CAMPAIGN_MANAGER"))).isTrue();
        assertThat(expression(WorkflowController.class, "publishWorkflow")).contains("workflow:write");
    }

    private static Stream<Method> mappedMethods(Class<?> controller) {
        return Stream.of(controller.getDeclaredMethods())
                .filter(AutomationControllerRbacTest::hasEndpointMapping);
    }

    private static boolean hasEndpointMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, GetMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class);
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
