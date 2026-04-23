package com.legent.common.exception;

import com.legent.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * Global exception handler for all REST controllers.
 * Maps exceptions to standardized ApiResponse error format.
 */
@Slf4j
@RestControllerAdvice

public class GlobalExceptionHandler {

    @ExceptionHandler(LegentException.class)
    public ResponseEntity<ApiResponse<Void>> handleLegentException(
            LegentException ex, HttpServletRequest request) {
        log.warn("Business exception [{}]: {} | path={}",
                ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return buildResponse(ex.getErrorCode(), ex.getMessage(), ex.getDetails(), ex.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", details);
        return buildResponse("VALIDATION_ERROR", "Request validation failed", details, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex) {
        log.warn("Missing parameter: {}", ex.getParameterName());
        return buildResponse("MISSING_PARAMETER", ex.getMessage(), null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        Class<?> type = ex.getRequiredType();
        String typeName = type != null ? type.getSimpleName() : "unknown";
        String detail = String.format("Parameter '%s' should be of type '%s'", ex.getName(), typeName);
        log.warn("Type mismatch: {}", detail);
        return buildResponse("TYPE_MISMATCH", detail, null, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                null,
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    // ── Helpers ──

    private ResponseEntity<ApiResponse<Void>> buildResponse(
            String code, String message, String details, HttpStatus status) {
        ApiResponse<Void> body = ApiResponse.error(code, message, details);
        return ResponseEntity.status(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String formatFieldError(FieldError error) {
        return String.format("%s: %s", error.getField(), error.getDefaultMessage());
    }
}
