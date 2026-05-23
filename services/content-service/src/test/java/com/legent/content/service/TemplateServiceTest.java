package com.legent.content.service;

import com.legent.common.constant.AppConstants;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.repository.EmailTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateServiceTest {

    @AfterEach
    void tearDown() {
        com.legent.security.TenantContext.clear();
    }

    @Test
    void testRenderTemplateWithPersonalization() {
        TemplateEngine engine = new TemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        engine.setTemplateResolver(resolver);
        
        TemplateService service = new TemplateService(null, engine, null, null);

        EmailTemplate template = new EmailTemplate();
        template.setHtmlContent("Hello, [[${name}]]!");
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", "Alice");
        String rendered = service.renderTemplate(template, vars);
        assertEquals("Hello, Alice!", rendered.trim());
    }

    @Test
    void searchTemplatesUsesBoundedFirstPageRequestWithinTenantWorkspace() {
        EmailTemplateRepository repository = mock(EmailTemplateRepository.class);
        TemplateService service = new TemplateService(repository, null, null, null);
        EmailTemplate template = new EmailTemplate();
        template.setId("template-1");
        template.setTenantId("tenant-1");
        template.setWorkspaceId("workspace-1");
        template.setName("Welcome");

        when(repository.searchByName(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("welcome"),
                any(Pageable.class)))
                .thenReturn(List.of(template));

        List<EmailTemplate> results = service.searchTemplates("tenant-1", "workspace-1", "welcome");

        assertEquals(1, results.size());
        assertSame(template, results.get(0));
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchByName(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("welcome"),
                pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(AppConstants.MAX_PAGE_SIZE, pageableCaptor.getValue().getPageSize());
    }
}
