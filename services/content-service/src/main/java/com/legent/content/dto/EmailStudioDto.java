package com.legent.content.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

public class EmailStudioDto {

    @Data
    public static class ContentBlockVersionRequest {
        @NotBlank
        private String content;
        private String styles;
        private String settings;
        private String changes;
        private Boolean publish = false;
    }

    @Data
    public static class ContentBlockVersionResponse {
        private String id;
        private String blockId;
        private Integer versionNumber;
        private String content;
        private String styles;
        private String settings;
        private String changes;
        private Boolean isPublished;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    public static class SnippetRequest {
        @NotBlank
        @Size(max = 128)
        private String snippetKey;
        @NotBlank
        private String name;
        private String snippetType = "HTML";
        @NotBlank
        private String content;
        private String description;
        private Boolean isGlobal = false;
    }

    @Data
    public static class SnippetResponse {
        private String id;
        private String snippetKey;
        private String name;
        private String snippetType;
        private String content;
        private String description;
        private Boolean isGlobal;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    public static class TokenRequest {
        @NotBlank
        @Size(max = 128)
        private String tokenKey;
        @NotBlank
        private String displayName;
        private String description;
        private String sourceType = "SUBSCRIBER";
        private String dataPath;
        private String defaultValue;
        private String sampleValue;
        private Boolean required = false;
    }

    @Data
    public static class TokenResponse {
        private String id;
        private String tokenKey;
        private String displayName;
        private String description;
        private String sourceType;
        private String dataPath;
        private String defaultValue;
        private String sampleValue;
        private Boolean required;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    public static class DynamicRuleRequest {
        @NotBlank
        @Size(max = 128)
        private String slotKey = "main";
        @NotBlank
        private String name;
        private Integer priority = 100;
        private String conditionField;
        private String operator = "ALWAYS";
        private String conditionValue;
        private String htmlContent;
        private String textContent;
        private Boolean active = true;
    }

    @Data
    public static class DynamicRuleResponse {
        private String id;
        private String templateId;
        private String slotKey;
        private String name;
        private Integer priority;
        private String conditionField;
        private String operator;
        private String conditionValue;
        private String htmlContent;
        private String textContent;
        private Boolean active;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    public static class BrandKitRequest {
        @NotBlank
        private String name;
        private String logoUrl;
        private String primaryColor;
        private String secondaryColor;
        private String fontFamily;
        private String footerHtml;
        private String legalText;
        private String defaultFromName;
        @Email
        private String defaultFromEmail;
        private Boolean isDefault = false;
    }

    @Data
    public static class BrandKitResponse {
        private String id;
        private String name;
        private String logoUrl;
        private String primaryColor;
        private String secondaryColor;
        private String fontFamily;
        private String footerHtml;
        private String legalText;
        private String defaultFromName;
        private String defaultFromEmail;
        private Boolean isDefault;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    public static class LandingPageRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String slug;
        private String htmlContent;
        private String metadata;
        private Boolean publish = false;
    }

    @Data
    public static class LandingPageResponse {
        private String id;
        private String name;
        private String slug;
        private String status;
        private String htmlContent;
        private String metadata;
        private String publishedAt;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    public static class RenderRequest {
        private Map<String, Object> variables;
        private Boolean publishedOnly = false;
        private Integer versionNumber;
        private String brandKitId;
    }

    @Data
    public static class RenderResponse {
        private String subject;
        private String htmlContent;
        private String textContent;
        private String validationStatus;
        private Integer versionNumber;
        private List<String> tokenKeys;
        private List<String> dynamicSlots;
        private List<String> errors;
        private List<String> warnings;
        private List<String> compatibilityWarnings;
        private Map<String, Object> metrics;
    }

    @Data
    public static class ValidationResponse {
        private String status;
        private String sanitizedHtml;
        private String textContent;
        private Integer linkCount;
        private Integer imageCount;
        private Integer imagesMissingAlt;
        private List<String> tokenKeys;
        private List<String> dynamicSlots;
        private List<String> errors;
        private List<String> warnings;
        private List<String> compatibilityWarnings;
        private Boolean ampUnsupported;
    }

    @Data
    public static class TestSendRequest {
        @Email
        @NotBlank
        private String email;
        private String recipientGroup;
        private String subjectOverride;
        private String brandKitId;
        private Map<String, Object> variables;
    }

    @Data
    public static class TestSendRecordResponse {
        private String id;
        private String templateId;
        private String recipientEmail;
        private String recipientGroup;
        private String subject;
        private String status;
        private String messageId;
        private String variablesJson;
        private String errorMessage;
        private String createdAt;
        private String updatedAt;
    }
}
