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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
        @GetMapping("/api/v1/public/landing-pages/spring-sale")
        Map<String, String> publicLandingPage() {
            return Map.of("slug", "spring-sale");
        }

        @GetMapping("/api/v1/landing-pages")
        Map<String, String> privateLandingPages() {
            return Map.of("scope", "workspace");
        }

        @PostMapping("/api/v1/rbac/content-write")
        @PreAuthorize("@rbacEvaluator.hasPermission('content:write', principal.roles) or @rbacEvaluator.hasPermission('template:*', principal.roles)")
        Map<String, String> contentWrite() {
            return Map.of("status", "allowed");
        }
    }
}
