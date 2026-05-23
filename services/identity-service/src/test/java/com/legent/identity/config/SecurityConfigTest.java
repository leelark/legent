package com.legent.identity.config;

import com.legent.security.JwtAuthenticationFilter;
import com.legent.security.JwtTokenProvider;
import com.legent.security.SecurityProperties;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebAppConfiguration
@ContextConfiguration(classes = {
        SecurityConfig.class,
        SecurityConfigTest.TestMvcConfig.class,
        SecurityConfigTest.TestSecurityBeans.class
})
@ExtendWith(SpringExtension.class)
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void authSubroutesRequiringAuthenticatedUserStayProtectedBeforeBroadAuthPermitAll() throws Exception {
        assertRequiresAuthentication(post("/api/v1/auth/invitations"));
        assertRequiresAuthentication(get("/api/v1/auth/invitations"));
        assertRequiresAuthentication(post("/api/v1/auth/delegation/exchange"));
    }

    @Test
    void intentionalPublicAuthRoutesStayPermitAll() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/signup")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/invitations/accept")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/forgot-password")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/reset-password")).andExpect(status().isOk());
    }

    private void assertRequiresAuthentication(RequestBuilder request) throws Exception {
        int status = mockMvc.perform(request)
                .andReturn()
                .getResponse()
                .getStatus();

        assertTrue(
                status == 401 || status == 403,
                "Expected unauthenticated request to be denied before controller, but got HTTP " + status);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class TestMvcConfig {

        @Bean
        AuthRouteProbeController authRouteProbeController() {
            return new AuthRouteProbeController();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestSecurityBeans {

        @Bean
        SecurityProperties securityProperties() {
            SecurityProperties properties = new SecurityProperties();
            properties.getJwt().setSecret("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
            properties.getCors().setAllowedOrigins(List.of("https://app.legent.test"));
            return properties;
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtTokenProvider.class));
        }
    }

    @Controller
    static class AuthRouteProbeController {

        @PostMapping({
                "/api/v1/auth/login",
                "/api/v1/auth/signup",
                "/api/v1/auth/refresh",
                "/api/v1/auth/invitations",
                "/api/v1/auth/invitations/accept",
                "/api/v1/auth/delegation/exchange",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password"
        })
        @ResponseBody
        ResponseEntity<Void> okPost() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/api/v1/auth/invitations")
        @ResponseBody
        ResponseEntity<Void> okGet() {
            return ResponseEntity.ok().build();
        }
    }
}
