package com.legent.content.controller;

import com.legent.common.constant.AppConstants;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.service.EmailRenderService;
import com.legent.content.service.TemplateService;
import com.legent.content.service.TemplateTestSendService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateControllerTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listTemplatesClampsInvalidPageAndSizeBeforeServiceCall() {
        TemplateService service = mock(TemplateService.class);
        TemplateController controller = controller(service);
        PageRequest expectedPage = PageRequest.of(0, AppConstants.DEFAULT_PAGE_SIZE);
        when(service.listTemplates("tenant-1", "workspace-1", expectedPage))
                .thenReturn(new PageImpl<>(List.of(), expectedPage, 42));

        setScope();

        var response = controller.listTemplates(-7, 0);

        assertThat(response.getPagination().getPage()).isZero();
        assertThat(response.getPagination().getSize()).isEqualTo(AppConstants.DEFAULT_PAGE_SIZE);
        assertThat(response.getPagination().getTotalElements()).isEqualTo(42);
        verify(service).listTemplates("tenant-1", "workspace-1", expectedPage);
    }

    @Test
    void listTemplatesClampsExcessiveSizeBeforeServiceCall() {
        TemplateService service = mock(TemplateService.class);
        TemplateController controller = controller(service);
        PageRequest expectedPage = PageRequest.of(2, AppConstants.MAX_PAGE_SIZE);
        when(service.listTemplates("tenant-1", "workspace-1", expectedPage))
                .thenReturn(new PageImpl<>(List.of(), expectedPage, 0));

        setScope();

        var response = controller.listTemplates(2, AppConstants.MAX_PAGE_SIZE + 500);

        assertThat(response.getPagination().getPage()).isEqualTo(2);
        assertThat(response.getPagination().getSize()).isEqualTo(AppConstants.MAX_PAGE_SIZE);
        verify(service).listTemplates("tenant-1", "workspace-1", expectedPage);
    }

    @Test
    void searchTemplatesKeepsListApiResponseAndTenantWorkspaceScope() {
        TemplateService service = mock(TemplateService.class);
        TemplateController controller = controller(service);
        when(service.searchTemplates("tenant-1", "workspace-1", "welcome"))
                .thenReturn(List.of(template("template-1", "Welcome")));

        setScope();

        var response = controller.searchTemplates("welcome");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getId()).isEqualTo("template-1");
        assertThat(response.getData().get(0).getName()).isEqualTo("Welcome");
        verify(service).searchTemplates("tenant-1", "workspace-1", "welcome");
    }

    private TemplateController controller(TemplateService service) {
        return new TemplateController(
                service,
                mock(TemplateTestSendService.class),
                mock(EmailRenderService.class));
    }

    private static void setScope() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    private static EmailTemplate template(String id, String name) {
        EmailTemplate template = new EmailTemplate();
        template.setId(id);
        template.setTenantId("tenant-1");
        template.setWorkspaceId("workspace-1");
        template.setName(name);
        template.setSubject("Hello");
        return template;
    }
}
