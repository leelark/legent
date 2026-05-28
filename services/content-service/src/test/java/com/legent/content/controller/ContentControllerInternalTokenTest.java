package com.legent.content.controller;

import com.legent.content.dto.EmailStudioDto;
import com.legent.content.service.EmailRenderService;
import com.legent.content.service.RenderedContentSnapshotService;
import com.legent.content.service.TemplateVersionService;
import com.legent.common.security.InternalServiceIdentity;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentControllerInternalTokenTest {

    private static final String INTERNAL_TOKEN = "internal-service-token-prod-1234567890abcdef";

    @Test
    void renderTemplateInternalUsesInternalApiTokenValidatorMatching() {
        EmailRenderService renderService = mock(EmailRenderService.class);
        ContentController controller = new ContentController(
                renderService,
                mock(TemplateVersionService.class),
                mock(RenderedContentSnapshotService.class));
        ReflectionTestUtils.setField(controller, "internalApiToken", INTERNAL_TOKEN);

        EmailStudioDto.RenderResponse renderResponse = new EmailStudioDto.RenderResponse();
        renderResponse.setSubject("Subject");
        renderResponse.setHtmlContent("<p>Hello</p>");
        renderResponse.setTextContent("Hello");
        when(renderService.render(eq("tenant-1"), eq("workspace-1"), eq("template-1"), any(EmailStudioDto.RenderRequest.class)))
                .thenReturn(renderResponse);

        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        Instant timestamp = Instant.now();
        try {
            var response = controller.renderTemplateInternal(
                    "template-1",
                    "  " + INTERNAL_TOKEN + "  ",
                    "campaign-service",
                    timestamp.toString(),
                    signature(
                            "campaign-service",
                            "tenant-1",
                            "workspace-1",
                            InternalServiceIdentity.scopedAction(
                                    InternalServiceIdentity.ACTION_CONTENT_TEMPLATE_RENDER,
                                    "template-1"),
                            timestamp),
                    Map.of("firstName", "Ada"));

            assertThat(response.getData().subject()).isEqualTo("Subject");
            verify(renderService).render(eq("tenant-1"), eq("workspace-1"), eq("template-1"), any(EmailStudioDto.RenderRequest.class));
        } finally {
            TenantContext.clear();
        }
    }

    private String signature(String serviceName,
                             String tenantId,
                             String workspaceId,
                             String action,
                             Instant timestamp) {
        return InternalServiceIdentity.sign(INTERNAL_TOKEN, serviceName, tenantId, workspaceId, action, timestamp);
    }
}
