package com.legent.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Externalized security properties.
 * Configured in application.yml under the legent.security prefix.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "legent.security")
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86400000; // 24 hours
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins;
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = List.of("*");
        private boolean allowCredentials = true;
        private long maxAge = 3600;
    }
    @jakarta.annotation.PostConstruct
    public void validate() {
        if (jwt.getSecret() == null || jwt.getSecret().isBlank() || jwt.getSecret().length() < 32) {
            throw new IllegalStateException("JWT secret must be explicitly configured and must be at least 32 characters long.");
        }
        if (cors.getAllowedOrigins() == null || cors.getAllowedOrigins().isEmpty()) {
            throw new IllegalStateException("CORS allowed origins must be explicitly configured.");
        }
    }
}
