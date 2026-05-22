package com.legent.audience.config;

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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void internalQueryActivityRouteReachesControllerWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/data-extensions/query-activities/internal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"route\":\"query-activity\"}"));
    }

    @Test
    void internalImportStartRouteReachesControllerWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/v1/imports/internal/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"route\":\"import-start\"}"));
    }

    @Test
    void workspaceAudienceRoutesStillRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/data-extensions")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(result -> assertNotEquals(200, result.getResponse().getStatus()));
    }

    @Test
    void methodSecurityDeniesAudienceWriteWithoutRequiredPermission() throws Exception {
        mockMvc.perform(post("/api/v1/rbac/audience-write")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void methodSecurityAllowsAudienceWriteForCampaignManager() throws Exception {
        mockMvc.perform(post("/api/v1/rbac/audience-write")
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
        @PostMapping("/api/v1/data-extensions/query-activities/internal")
        Map<String, String> internalQueryActivity() {
            return Map.of("route", "query-activity");
        }

        @PostMapping("/api/v1/imports/internal/start")
        @ResponseStatus(HttpStatus.ACCEPTED)
        Map<String, String> internalImportStart() {
            return Map.of("route", "import-start");
        }

        @GetMapping("/api/v1/data-extensions")
        Map<String, String> dataExtensions() {
            return Map.of("scope", "workspace");
        }

        @PostMapping("/api/v1/rbac/audience-write")
        @PreAuthorize("@rbacEvaluator.hasPermission('audience:write', principal.roles)")
        Map<String, String> audienceWrite() {
            return Map.of("status", "allowed");
        }
    }
}
