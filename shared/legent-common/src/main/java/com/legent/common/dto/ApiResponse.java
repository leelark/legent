package com.legent.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standardized API response envelope used by every controller.
 * <p>
 * Success:  { success: true,  data: {...},  error: null }
 * Failure:  { success: false, data: null,   error: {...} }
 *
 * @param <T> the type of the response data payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private ErrorDetail error;
    private Meta meta;

    // ── Factory methods ──

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(Meta.now())
                .build();
    }

    public static <T> ApiResponse<T> ok(T data, String requestId, String tenantId) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(Meta.of(requestId, tenantId))
                .build();
    }

    public static <T> ApiResponse<T> error(String errorCode, String message, String details) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorDetail.of(errorCode, message, details))
                .meta(Meta.now())
                .build();
    }

    public static <T> ApiResponse<T> error(ErrorDetail errorDetail) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(errorDetail)
                .meta(Meta.now())
                .build();
    }

    // ── Nested types ──

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String errorCode;
        private String message;
        private String details;
        private Instant timestamp;

        public static ErrorDetail of(String errorCode, String message, String details) {
            return ErrorDetail.builder()
                    .errorCode(errorCode)
                    .message(message)
                    .details(details)
                    .timestamp(Instant.now())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private Instant timestamp;
        private String requestId;
        private String tenantId;

        public static Meta now() {
            return Meta.builder().timestamp(Instant.now()).build();
        }

        public static Meta of(String requestId, String tenantId) {
            return Meta.builder()
                    .timestamp(Instant.now())
                    .requestId(requestId)
                    .tenantId(tenantId)
                    .build();
        }
    }
}
