package com.legent.audience.dto;

import java.util.List;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;
import lombok.*;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriberListDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank @Size(max = 255) private String name;
        @Size(max = 2000) private String description;
        private String listType;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRequest {
        @Size(max = 255) private String name;
        @Size(max = 2000) private String description;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MembershipRequest {
        @NotNull @Size(min = 1, max = 10000)
        private List<String> subscriberIds;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Response {
        private String id;
        private String name;
        private String description;
        private String listType;
        private String status;
        private long memberCount;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
