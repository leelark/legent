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
        @Size(max = 255) private String subscriberKey;
        @NotBlank(message = "Email is required") @Email
        @Size(max = 320) private String email;
        @Size(max = 128) private String firstName;
        @Size(max = 128) private String lastName;
        @Size(max = 30) private String phone;
        private java.time.LocalDate dateOfBirth;
        @Size(max = 32) private String gender;
        @Size(max = 255) private String company;
        @Size(max = 255) private String jobTitle;
        @Size(max = 255) private String industry;
        @Size(max = 255) private String department;
        @Size(max = 128) private String country;
        @Size(max = 128) private String state;
        @Size(max = 128) private String city;
        @Size(max = 64) private String language;
        private String locale;
        private String timezone;
        private String source;
        private String leadSource;
        private String acquisitionChannel;
        private String campaignSource;
        private List<String> tags;
        private List<String> categories;
        private String profileImageUrl;
        private String internalNotes;
        private String teamId;
        private String assignedOwnerId;
        private Map<String, Object> customFields;
        private Map<String, Object> channelPreferences;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRequest {
        @Email @Size(max = 320) private String email;
        @Size(max = 128) private String firstName;
        @Size(max = 128) private String lastName;
        @Size(max = 30) private String phone;
        private java.time.LocalDate dateOfBirth;
        @Size(max = 32) private String gender;
        @Size(max = 255) private String company;
        @Size(max = 255) private String jobTitle;
        @Size(max = 255) private String industry;
        @Size(max = 255) private String department;
        @Size(max = 128) private String country;
        @Size(max = 128) private String state;
        @Size(max = 128) private String city;
        @Size(max = 64) private String language;
        private String status;
        private String locale;
        private String timezone;
        private String source;
        private String leadSource;
        private String acquisitionChannel;
        private String campaignSource;
        private List<String> tags;
        private List<String> categories;
        private String profileImageUrl;
        private String internalNotes;
        private String teamId;
        private String assignedOwnerId;
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
        private String lifecycleStage;
        private String emailFormat;
        private String locale;
        private String language;
        private String timezone;
        private String source;
        private String leadSource;
        private String acquisitionChannel;
        private String campaignSource;
        private String company;
        private String jobTitle;
        private String industry;
        private String department;
        private String country;
        private String state;
        private String city;
        private java.time.LocalDate dateOfBirth;
        private String gender;
        private List<String> tags;
        private List<String> categories;
        private String profileImageUrl;
        private String internalNotes;
        private String teamId;
        private String assignedOwnerId;
        private Map<String, Object> customFields;
        private Map<String, Object> channelPreferences;
        private int openScore;
        private int clickScore;
        private int conversionScore;
        private int recencyScore;
        private int frequencyScore;
        private int engagementScore;
        private int activityScore;
        private int totalScore;
        private List<Map<String, Object>> timeline;
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

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MergeRequest {
        @NotBlank
        private String winnerSubscriberId;
        @NotNull
        @Size(min = 1)
        private List<String> mergedSubscriberIds;
        private String reason;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BulkActionRequest {
        @NotNull
        @Size(min = 1)
        private List<String> subscriberIds;
        @NotBlank
        private String action; // DELETE | BLOCK | UNBLOCK | ACTIVATE | INACTIVE
        private String value;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LifecycleUpdateRequest {
        @NotBlank
        private String stage;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ScoreUpdateRequest {
        private Integer openScore;
        private Integer clickScore;
        private Integer conversionScore;
        private Integer recencyScore;
        private Integer frequencyScore;
        private Integer engagementScore;
        private Integer activityScore;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ActivityTimelineResponse {
        private String subscriberId;
        private List<Map<String, Object>> entries;
    }
}
