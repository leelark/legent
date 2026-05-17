package com.legent.campaign.service;

import com.legent.campaign.client.ContentServiceClient;
import com.legent.common.event.EmailContentReference;
import com.legent.common.exception.ValidationException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RenderedContentReferenceService {

    private final ContentServiceClient contentServiceClient;

    public EmailContentReference createReference(CreateRequest request, boolean inlineFallbackIncluded) {
        validate(request);
        return contentServiceClient.createRenderedContentReference(
                new ContentServiceClient.RenderedContentSnapshotRequest(
                        request.tenantId(),
                        request.workspaceId(),
                        request.campaignId(),
                        request.jobId(),
                        request.batchId(),
                        request.messageId(),
                        request.contentId(),
                        request.subject(),
                        request.htmlBody(),
                        request.textBody()),
                inlineFallbackIncluded);
    }

    public StoredRenderedContent readReference(String tenantId, String workspaceId, String referenceId) {
        require(tenantId, "tenantId");
        require(workspaceId, "workspaceId");
        if (isBlank(referenceId)) {
            throw new ValidationException("contentReference", "contentReference is required");
        }
        ContentServiceClient.StoredRenderedContent content =
                contentServiceClient.readRenderedContentReference(tenantId, workspaceId, referenceId);
        return new StoredRenderedContent(
                content.subject(),
                content.htmlBody(),
                content.textBody(),
                content.metadata());
    }

    private void validate(CreateRequest request) {
        if (request == null) {
            throw new ValidationException("contentReference", "Rendered content reference request is required");
        }
        require(request.tenantId(), "tenantId");
        require(request.workspaceId(), "workspaceId");
        require(request.campaignId(), "campaignId");
        require(request.jobId(), "jobId");
        require(request.batchId(), "batchId");
        require(request.messageId(), "messageId");
        require(request.contentId(), "contentId");
        require(request.subject(), "subject");
        require(request.htmlBody(), "htmlBody");
    }

    private void require(String value, String field) {
        if (isBlank(value)) {
            throw new ValidationException(field, field + " is required for rendered content reference");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CreateRequest(String tenantId,
                                String workspaceId,
                                String campaignId,
                                String jobId,
                                String batchId,
                                String messageId,
                                String contentId,
                                String subject,
                                String htmlBody,
                                String textBody) {
    }

    public record StoredRenderedContent(String subject,
                                        String htmlBody,
                                        String textBody,
                                        Map<String, String> metadata) {
    }
}
