package com.legent.content.controller;

import com.legent.security.RbacEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.BeanResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ContentControllerRbacTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
            AssetController.class,
            ContentBlockController.class,
            ContentController.class,
            EmailController.class,
            EmailStudioController.class,
            SendGovernancePolicyController.class,
            TemplateController.class,
            TemplateVersionController.class,
            TemplateWorkflowController.class
    );

    private static final Set<String> READ_AUTHORIZATION_EXCEPTIONS = Set.of(
            methodId(ContentController.class, "getRenderedContentSnapshot"),
            methodId(EmailStudioController.class, "getPublicLandingPage"),
            methodId(SendGovernancePolicyController.class, "getInternal")
    );

    @Test
    void unsafeContentControllerEndpointsDeclareRbac() {
        List<String> missing = CONTROLLERS.stream()
                .flatMap(controller -> Stream.of(controller.getDeclaredMethods())
                        .filter(ContentControllerRbacTest::isUnsafeMapping)
                        .filter(method -> preAuthorize(method) == null)
                        .map(method -> controller.getSimpleName() + "#" + method.getName()))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void protectedContentReadEndpointsDeclareReadRbac() {
        List<String> missing = CONTROLLERS.stream()
                .flatMap(controller -> Stream.of(controller.getDeclaredMethods())
                        .filter(ContentControllerRbacTest::isReadMapping)
                        .filter(method -> !isReadAuthorizationException(controller, method))
                        .filter(method -> !hasReadRbac(method))
                        .map(method -> methodId(controller, method.getName())))
                .toList();

        assertThat(missing).isEmpty();
    }

    @Test
    void publicAndInternalContentReadExceptionsStayExplicit() {
        assertThat(READ_AUTHORIZATION_EXCEPTIONS).containsExactlyInAnyOrder(
                methodId(ContentController.class, "getRenderedContentSnapshot"),
                methodId(EmailStudioController.class, "getPublicLandingPage"),
                methodId(SendGovernancePolicyController.class, "getInternal"));
        assertThat(expression(ContentController.class, "getRenderedContentSnapshot")).isEqualTo("permitAll()");
        assertThat(expression(SendGovernancePolicyController.class, "getInternal")).isEqualTo("permitAll()");
        assertThat(preAuthorize(method(EmailStudioController.class, "getPublicLandingPage"))).isNull();
    }

    @Test
    void renderAndTestSendEndpointsUseReadAndWriteContentPermissions() {
        assertThat(expression(ContentController.class, "renderTemplate"))
                .contains("content:read", "template:*");
        assertThat(expression(ContentController.class, "getLatestVersion"))
                .contains("content:read", "template:*");
        assertThat(expression(EmailStudioController.class, "renderBuilderLayout"))
                .contains("content:read", "template:*");
        assertThat(expression(EmailStudioController.class, "renderTemplate"))
                .contains("content:read", "template:*");
        assertThat(expression(SendGovernancePolicyController.class, "get"))
                .contains("content:read", "template:*");
        assertThat(expression(TemplateController.class, "previewTemplate"))
                .contains("content:read", "template:*");

        assertThat(expression(SendGovernancePolicyController.class, "create"))
                .contains("content:write", "template:*");
        assertThat(expression(SendGovernancePolicyController.class, "update"))
                .contains("content:write", "template:*");
        assertThat(expression(EmailStudioController.class, "createTestSend"))
                .contains("content:write", "template:*");
        assertThat(expression(EmailStudioController.class, "createTestSendMatrix"))
                .contains("content:write", "template:*");
        assertThat(expression(TemplateController.class, "testSend"))
                .contains("content:write", "template:*");
    }

    @Test
    void publishAndDeleteEndpointsUseLandingPagePermissionPatterns() {
        assertThat(expression(EmailStudioController.class, "deleteLandingPage"))
                .contains("content:delete", "content:*", "template:*");
        assertThat(expression(ContentBlockController.class, "deleteBlock"))
                .contains("content:delete", "content:*", "template:*");
        assertThat(expression(SendGovernancePolicyController.class, "delete"))
                .contains("content:delete", "content:*", "template:*");
        assertThat(expression(TemplateController.class, "deleteTemplate"))
                .contains("content:delete", "content:*", "template:*");

        assertThat(expression(EmailStudioController.class, "publishLandingPage"))
                .contains("content:publish", "content:*", "template:*");
        assertThat(expression(ContentBlockController.class, "publishVersion"))
                .contains("content:publish", "content:*", "template:*");
        assertThat(expression(TemplateWorkflowController.class, "publishTemplate"))
                .contains("content:publish", "content:*", "template:*");
        assertThat(expression(TemplateVersionController.class, "publishVersion"))
                .contains("content:publish", "content:*", "template:*");
    }

    @Test
    void latestVersionEndpointAllowsContentReadOrTemplatePermission() {
        assertThat(preAuthorizeGrants(ContentController.class, "getLatestVersion", Set.of("content:read")))
                .isTrue();
        assertThat(preAuthorizeGrants(ContentController.class, "getLatestVersion", Set.of("template:*")))
                .isTrue();
    }

    @Test
    void latestVersionEndpointDeniesUnrelatedContentPermission() {
        assertThat(preAuthorizeGrants(ContentController.class, "getLatestVersion", Set.of("content:write")))
                .isFalse();
        assertThat(expression(ContentController.class, "getLatestVersion"))
                .doesNotContain("permitAll", "content:write", "content:delete", "content:publish");
    }

    private static boolean isUnsafeMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, PostMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PutMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, DeleteMapping.class)
                || AnnotatedElementUtils.hasAnnotation(method, PatchMapping.class);
    }

    private static boolean isReadMapping(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, GetMapping.class);
    }

    private static boolean isReadAuthorizationException(Class<?> controller, Method method) {
        return READ_AUTHORIZATION_EXCEPTIONS.contains(methodId(controller, method.getName()));
    }

    private static boolean hasReadRbac(Method method) {
        PreAuthorize annotation = preAuthorize(method);
        if (annotation == null) {
            return false;
        }
        return annotation.value().contains("content:read") || annotation.value().contains("template:*");
    }

    private static String expression(Class<?> controller, String methodName) {
        PreAuthorize annotation = preAuthorize(method(controller, methodName));
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

    private static String methodId(Class<?> controller, String methodName) {
        return controller.getSimpleName() + "#" + methodName;
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
