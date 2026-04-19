package com.legent.audience.dto;

import java.util.List;

import java.util.Map;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriberDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Subscriber key is required")
        @Size(max = 255) private String subscriberKey;
        @NotBlank(message = "Email is required") @Email
        @Size(max = 320) private String email;
        @Size(max = 128) private String firstName;
        @Size(max = 128) private String lastName;
        @Size(max = 30) private String phone;
        private String locale;
        private String timezone;
        private String source;
        private Map<String, Object> customFields;
        private Map<String, Object> channelPreferences;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRequest {
        @Email @Size(max = 320) private String email;
        @Size(max = 128) private String firstName;
        @Size(max = 128) private String lastName;
        @Size(max = 30) private String phone;
        private String status;
        private String locale;
        private String timezone;
        private Map<String, Object> customFields;
        private Map<String, Object> channelPreferences;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BulkUpsertRequest {
        @NotNull @Size(min = 1, max = 10000)
        private List<CreateRequest> subscribers;
        private boolean updateExisting;
        private String deduplicationKey; // subscriber_key | email
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SearchRequest {
        private String query;
        private String status;
        private String listId;
        private String segmentId;
        private Instant createdAfter;
        private Instant createdBefore;
        private String sortBy;
        private String sortDir;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private String id;
        private String subscriberKey;
        private String email;
        private String firstName;
        private String lastName;
        private String phone;
        private String status;
        private String emailFormat;
        private String locale;
        private String timezone;
        private String source;
        private Map<String, Object> customFields;
        private Map<String, Object> channelPreferences;
        private Instant lastActivityAt;
        private Instant subscribedAt;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BulkUpsertResponse {
        private int created;
        private int updated;
        private int errors;
        private List<BulkError> errorDetails;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BulkError {
        private int index;
        private String subscriberKey;
        private String message;
    }
}
