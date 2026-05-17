package com.legent.campaign.service;

import com.legent.campaign.client.ContentServiceClient;
import com.legent.common.event.EmailContentReference;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenderedContentReferenceServiceTest {

    @Mock private ContentServiceClient contentServiceClient;

    private RenderedContentReferenceService service;

    @BeforeEach
    void setUp() {
        service = new RenderedContentReferenceService(contentServiceClient);
    }

    @Test
    void createReferencePersistsSnapshotThroughContentService() {
        RenderedContentReferenceService.CreateRequest request = request();
        EmailContentReference expected = EmailContentReference.builder()
                .referenceId("cr_ref")
                .storageBackend("content-service")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .campaignId("campaign-1")
                .jobId("job-1")
                .batchId("batch-1")
                .messageId("message-1")
                .contentId("content-1")
                .inlineFallbackIncluded(false)
                .build();
        when(contentServiceClient.createRenderedContentReference(any(), eq(false))).thenReturn(expected);

        EmailContentReference reference = service.createReference(request, false);

        ArgumentCaptor<ContentServiceClient.RenderedContentSnapshotRequest> requestCaptor =
                ArgumentCaptor.forClass(ContentServiceClient.RenderedContentSnapshotRequest.class);
        verify(contentServiceClient).createRenderedContentReference(requestCaptor.capture(), eq(false));
        assertEquals(expected, reference);
        assertEquals("content-service", reference.getStorageBackend());
        assertEquals("tenant-1", reference.getTenantId());
        assertEquals("workspace-1", reference.getWorkspaceId());
        assertEquals("Rendered", requestCaptor.getValue().subject());
        assertEquals("<p>Hello</p>", requestCaptor.getValue().htmlBody());
    }

    @Test
    void readReferenceDelegatesTenantWorkspaceScopedLookup() {
        when(contentServiceClient.readRenderedContentReference("tenant-1", "workspace-1", "cr_ref"))
                .thenReturn(new ContentServiceClient.StoredRenderedContent(
                        "Rendered",
                        "<p>Hello</p>",
                        "Hello",
                        Map.of("tenantId", "tenant-1", "workspaceId", "workspace-1")));

        RenderedContentReferenceService.StoredRenderedContent content =
                service.readReference("tenant-1", "workspace-1", "cr_ref");

        assertEquals("Rendered", content.subject());
        assertEquals("<p>Hello</p>", content.htmlBody());
        assertEquals("Hello", content.textBody());
        verify(contentServiceClient).readRenderedContentReference("tenant-1", "workspace-1", "cr_ref");
    }

    private RenderedContentReferenceService.CreateRequest request() {
        return new RenderedContentReferenceService.CreateRequest(
                "tenant-1",
                "workspace-1",
                "campaign-1",
                "job-1",
                "batch-1",
                "message-1",
                "content-1",
                "Rendered",
                "<p>Hello</p>",
                "Hello");
    }
}
