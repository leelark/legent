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
        private Integer priority;
        private Integer maxSendRate;
    }
}
