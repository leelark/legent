package com.legent.audience.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

public class PreferenceDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private Map<String, Object> topicSubscriptions;
        private Map<String, Object> channelPreferences;
        private String communicationFrequency;
        private String preferredLanguage;
        private String preferredBrand;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PauseRequest {
        private Instant pausedUntil;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnsubscribeRequest {
        @NotBlank
        private String scope; // GLOBAL | PARTIAL
        private String reason;
        private String topic;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String subscriberId;
        private String status;
        private Map<String, Object> topicSubscriptions;
        private Map<String, Object> channelPreferences;
        private String communicationFrequency;
        private String preferredLanguage;
        private String preferredBrand;
        private Instant pausedUntil;
    }
}
