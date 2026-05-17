package com.legent.content.controller;

import com.legent.content.dto.EmailStudioDto;
import com.legent.content.service.EmailRenderService;
import com.legent.content.service.RenderedContentSnapshotService;
import com.legent.content.service.TemplateVersionService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

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
        try {
            var response = controller.renderTemplateInternal(
                    "template-1",
                    "  " + INTERNAL_TOKEN + "  ",
                    Map.of("firstName", "Ada"));

            assertThat(response.getData().subject()).isEqualTo("Subject");
            verify(renderService).render(eq("tenant-1"), eq("workspace-1"), eq("template-1"), any(EmailStudioDto.RenderRequest.class));
        } finally {
            TenantContext.clear();
        }
    }
}
