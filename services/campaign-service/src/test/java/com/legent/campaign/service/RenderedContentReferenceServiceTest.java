package com.legent.campaign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.cache.service.CacheService;
import com.legent.common.event.EmailContentReference;
import com.legent.common.exception.NotFoundException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenderedContentReferenceServiceTest {

    @Mock private CacheService cacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RenderedContentReferenceService service;

    @BeforeEach
    void setUp() {
        service = new RenderedContentReferenceService(cacheService, objectMapper);
        ReflectionTestUtils.setField(service, "contentReferenceTtl", Duration.ofHours(72));
    }

    @Test
    void createReferenceStoresRenderedContentAndReturnsMetadataOnly() throws Exception {
        RenderedContentReferenceService.CreateRequest request = request();

        EmailContentReference reference = service.createReference(request, false);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(cacheService).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofHours(72)));

        assertTrue(keyCaptor.getValue().startsWith("email:content:cr_"));
        assertEquals(reference.getReferenceId(), keyCaptor.getValue().substring("email:content:".length()));
        assertEquals("redis", reference.getStorageBackend());
        assertEquals("tenant-1", reference.getTenantId());
        assertEquals("workspace-1", reference.getWorkspaceId());
        assertEquals("content-1", reference.getContentId());
        assertEquals(32 + "cr_".length(), reference.getReferenceId().length());
        assertFalse(reference.getInlineFallbackIncluded());
        assertNotNull(reference.getHtmlSha256());
        assertEquals("<p>Hello</p>".getBytes(java.nio.charset.StandardCharsets.UTF_8).length, reference.getHtmlBytes());

        Map<String, String> stored = objectMapper.readValue(valueCaptor.getValue(), new TypeReference<>() {});
        assertEquals("Rendered", stored.get("subject"));
        assertEquals("<p>Hello</p>", stored.get("htmlBody"));
        assertEquals("Hello", stored.get("textBody"));
        assertEquals("tenant-1", stored.get("tenantId"));
        assertEquals("workspace-1", stored.get("workspaceId"));
    }

    @Test
    void readReferenceReturnsStoredContentWithinTenantWorkspaceScope() throws Exception {
        String serialized = objectMapper.writeValueAsString(Map.of(
                "tenantId", "tenant-1",
                "workspaceId", "workspace-1",
                "subject", "Rendered",
                "htmlBody", "<p>Hello</p>",
                "textBody", "Hello"));
        when(cacheService.get("email:content:cr_ref", String.class)).thenReturn(Optional.of(serialized));

        RenderedContentReferenceService.StoredRenderedContent content =
                service.readReference("tenant-1", "workspace-1", "cr_ref");

        assertEquals("Rendered", content.subject());
        assertEquals("<p>Hello</p>", content.htmlBody());
        assertEquals("Hello", content.textBody());
    }

    @Test
    void readReferenceRejectsCrossWorkspaceAccess() throws Exception {
        String serialized = objectMapper.writeValueAsString(Map.of(
                "tenantId", "tenant-1",
                "workspaceId", "workspace-1",
                "subject", "Rendered",
                "htmlBody", "<p>Hello</p>"));
        when(cacheService.get("email:content:cr_ref", String.class)).thenReturn(Optional.of(serialized));

        assertThrows(NotFoundException.class,
                () -> service.readReference("tenant-1", "workspace-2", "cr_ref"));
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
