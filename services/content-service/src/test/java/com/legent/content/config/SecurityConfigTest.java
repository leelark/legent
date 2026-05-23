package com.legent.content.config;

import com.legent.security.JwtAuthenticationFilter;
import com.legent.security.JwtTokenProvider;
import com.legent.security.RbacEvaluator;
import com.legent.security.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = SecurityConfigTest.TestApp.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                "legent.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "legent.security.cors.allowed-origins=http://localhost:3000"
        }
)
@AutoConfigureMockMvc
class SecurityConfigTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-prod-1234567890abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TestController testController;

    @Test
    void publicLandingPagesAreAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/public/landing-pages/spring-sale")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"slug\":\"spring-sale\"}"));
    }

    @Test
    void workspaceLandingPagesStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/landing-pages")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertNotEquals(200, result.getResponse().getStatus()));
    }

    @Test
    void internalSendGovernanceLookupReachesControllerWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/content/send-governance-policies/policy-1/internal")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"policyId\":\"policy-1\"}"));
    }

    @Test
    void internalRenderAndSnapshotRoutesReachControllerTokenGuardWithoutJwt() throws Exception {
        testController.resetInternalGuardHits();

        mockMvc.perform(post("/api/v1/content/template-1/render/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/content/rendered-content/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/content/rendered-content/ref-1/internal")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        assertThat(testController.internalGuardHits()).containsExactly(
                "render:template-1",
                "snapshot:create",
                "snapshot:read:ref-1"
        );
    }

    @Test
    void internalRenderAndSnapshotRoutesAllowValidInternalTokenWithoutJwt() throws Exception {
        testController.resetInternalGuardHits();

        mockMvc.perform(post("/api/v1/content/template-1/render/internal")
                        .header("X-Internal-Token", "  " + INTERNAL_TOKEN + "  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"route\":\"internal-render\",\"templateId\":\"template-1\"}"));
        mockMvc.perform(post("/api/v1/content/rendered-content/internal")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"route\":\"snapshot-create\"}"));
        mockMvc.perform(get("/api/v1/content/rendered-content/ref-1/internal")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"route\":\"snapshot-read\",\"referenceId\":\"ref-1\"}"));

        assertThat(testController.internalGuardHits()).containsExactly(
                "render:template-1",
                "snapshot:create",
                "snapshot:read:ref-1"
        );
    }

    @Test
    void protectedContentRoutesRequireJwt() throws Exception {
        testController.resetProtectedContentHits();

        mockMvc.perform(post("/api/v1/content/template-1/render")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertNotEquals(200, result.getResponse().getStatus()));
        mockMvc.perform(get("/api/v1/content/template-1/versions/latest")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertNotEquals(200, result.getResponse().getStatus()));

        assertThat(testController.protectedContentHits()).isEmpty();
    }

    @Test
    void protectedContentRoutesApplyRbacAfterJwt() throws Exception {
        testController.resetProtectedContentHits();

        mockMvc.perform(post("/api/v1/content/template-1/render")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ANALYST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/content/template-1/versions/latest")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ANALYST"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        assertThat(testController.protectedContentHits()).isEmpty();

        mockMvc.perform(post("/api/v1/content/template-1/render")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("CAMPAIGN_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"route\":\"protected-render\",\"templateId\":\"template-1\"}"));
        mockMvc.perform(get("/api/v1/content/template-1/versions/latest")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("CAMPAIGN_MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"route\":\"protected-latest-version\",\"templateId\":\"template-1\"}"));

        assertThat(testController.protectedContentHits()).containsExactly(
                "render:template-1",
                "latest-version:template-1"
        );
    }

    @Test
    void methodSecurityDeniesContentWriteWithoutRequiredPermission() throws Exception {
        mockMvc.perform(post("/api/v1/rbac/content-write")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ANALYST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void methodSecurityAllowsContentWriteForTemplateManager() throws Exception {
        mockMvc.perform(post("/api/v1/rbac/content-write")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("CAMPAIGN_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"allowed\"}"));
    }

    private String bearerToken(String role) {
        String token = jwtTokenProvider.generateToken("user_123", "tenant_123", Map.of(
                "roles", java.util.List.of(role),
                "workspaceId", "workspace_123"
        ));
        return "Bearer " + token;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import({SecurityConfig.class, TestController.class, TestSecurityBeans.class})
    static class TestApp {
    }

    @TestConfiguration
    static class TestSecurityBeans {
        @Bean
        SecurityProperties securityProperties() {
            SecurityProperties properties = new SecurityProperties();
            properties.getJwt().setSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
            properties.getCors().setAllowedOrigins(java.util.List.of("http://localhost:3000"));
            return properties;
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", 86400000);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
            return new JwtAuthenticationFilter(jwtTokenProvider);
        }

        @Bean
        RbacEvaluator rbacEvaluator() {
            return new RbacEvaluator();
        }
    }

    @RestController
    static class TestController {
        private final List<String> internalGuardHits = new CopyOnWriteArrayList<>();
        private final List<String> protectedContentHits = new CopyOnWriteArrayList<>();

        @GetMapping("/api/v1/public/landing-pages/spring-sale")
        Map<String, String> publicLandingPage() {
            return Map.of("slug", "spring-sale");
        }

        @GetMapping("/api/v1/landing-pages")
        Map<String, String> privateLandingPages() {
            return Map.of("scope", "workspace");
        }

        @GetMapping("/api/v1/content/send-governance-policies/{policyId}/internal")
        Map<String, String> internalSendGovernancePolicy(@PathVariable String policyId) {
            return Map.of("policyId", policyId);
        }

        @PostMapping("/api/v1/content/{templateId}/render/internal")
        Map<String, String> internalRender(
                @PathVariable String templateId,
                @RequestHeader(name = "X-Internal-Token", required = false) String token) {
            requireInternalToken("render:" + templateId, token);
            return Map.of("route", "internal-render", "templateId", templateId);
        }

        @PostMapping("/api/v1/content/rendered-content/internal")
        Map<String, String> createRenderedContentSnapshot(
                @RequestHeader(name = "X-Internal-Token", required = false) String token) {
            requireInternalToken("snapshot:create", token);
            return Map.of("route", "snapshot-create");
        }

        @GetMapping("/api/v1/content/rendered-content/{referenceId}/internal")
        Map<String, String> getRenderedContentSnapshot(
                @PathVariable String referenceId,
                @RequestHeader(name = "X-Internal-Token", required = false) String token) {
            requireInternalToken("snapshot:read:" + referenceId, token);
            return Map.of("route", "snapshot-read", "referenceId", referenceId);
        }

        @PostMapping("/api/v1/content/{templateId}/render")
        @PreAuthorize("@rbacEvaluator.hasPermission('content:read', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
        Map<String, String> protectedRender(@PathVariable String templateId) {
            protectedContentHits.add("render:" + templateId);
            return Map.of("route", "protected-render", "templateId", templateId);
        }

        @GetMapping("/api/v1/content/{templateId}/versions/latest")
        @PreAuthorize("@rbacEvaluator.hasPermission('content:read', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
        Map<String, String> protectedLatestVersion(@PathVariable String templateId) {
            protectedContentHits.add("latest-version:" + templateId);
            return Map.of("route", "protected-latest-version", "templateId", templateId);
        }

        @PostMapping("/api/v1/rbac/content-write")
        @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
        Map<String, String> contentWrite() {
            return Map.of("status", "allowed");
        }

        void resetInternalGuardHits() {
            internalGuardHits.clear();
        }

        List<String> internalGuardHits() {
            return List.copyOf(internalGuardHits);
        }

        void resetProtectedContentHits() {
            protectedContentHits.clear();
        }

        List<String> protectedContentHits() {
            return List.copyOf(protectedContentHits);
        }

        private void requireInternalToken(String routeId, String token) {
            internalGuardHits.add(routeId);
            if (!INTERNAL_TOKEN.equals(token == null ? "" : token.trim())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
            }
        }
    }
}
