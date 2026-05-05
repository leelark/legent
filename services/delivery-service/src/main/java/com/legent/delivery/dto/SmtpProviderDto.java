package com.legent.delivery.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


public class SmtpProviderDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String id;
        private String name;
        private String type;
        private String host;
        private Integer port;
        private String username;
        private boolean isActive;
        private Integer priority;
        private Integer maxSendRate;
        private boolean healthCheckEnabled;
        private String healthCheckUrl;
        private Integer healthCheckIntervalSeconds;
        private String healthStatus;
        private Instant lastHealthCheckAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String name;
        private String type; // SMTP, AWS_SES, MOCK
        private String host;
        private Integer port;
        private String username;
        private String password;
        private Boolean isActive;
        private Integer priority;
        private Integer maxSendRate;
        private Boolean healthCheckEnabled;
        private String healthCheckUrl;
        private Integer healthCheckIntervalSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderHealthResponse {
        private String id;
        private String name;
        private String type;
        private boolean isActive;
        private String healthStatus;
        private Instant lastHealthCheckAt;
        private Integer priority;
    }
}
