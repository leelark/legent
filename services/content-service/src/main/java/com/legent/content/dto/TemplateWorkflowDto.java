package com.legent.content.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

public class TemplateWorkflowDto {

    @Data
    public static class DraftRequest {
        @Size(max = 500)
        private String subject;
        private String htmlContent;
        private String textContent;
    }

    @Data
    public static class SubmitApprovalRequest {
        private String comments;
    }

    @Data
    public static class ApprovalActionRequest {
        private String comments;
        private String reason;
    }

    @Data
    public static class PublishRequest {
        private Integer versionNumber;
    }

    @Data
    public static class TemplateApprovalResponse {
        private String id;
        private String templateId;
        private Integer versionNumber;
        private String requestedBy;
        private String requestedAt;
        private String status;
        private String approvedBy;
        private String approvedAt;
        private String rejectionReason;
        private String comments;
        private String updatedAt;
    }

    @Data
    public static class PreviewRequest {
        private Map<String, Object> variables;
        private String mode;
        private Boolean darkMode;
    }

    @Data
    public static class PreviewResponse {
        private String subject;
        private String htmlContent;
        private String textContent;
        private List<String> warnings;
    }

    @Data
    public static class ValidateRequest {
        private String htmlContent;
    }

    @Data
    public static class ValidateResponse {
        private int linkCount;
        private int brokenLinkCount;
        private int imageCount;
        private int imagesMissingAlt;
        private List<String> brokenLinks;
        private List<String> warnings;
    }

    @Data
    public static class ImportHtmlRequest {
        @NotBlank
        @Size(max = 255)
        private String name;
        @NotBlank
        @Size(max = 500)
        private String subject;
        @NotBlank
        private String htmlContent;
        private String textContent;
        private String category;
        private List<String> tags;
        private String metadata;
        private Boolean publish;
    }

    @Data
    public static class ExportHtmlResponse {
        private String id;
        private String name;
        private String subject;
        private String htmlContent;
        private String textContent;
        private String exportedAt;
    }

    @Data
    public static class TestSendRequest {
        @Email
        @NotBlank
        private String email;
        private String subjectOverride;
        private Map<String, Object> variables;
    }
}
