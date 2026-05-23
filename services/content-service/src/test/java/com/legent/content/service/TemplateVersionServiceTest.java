package com.legent.content.service;

import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.event.ContentEventPublisher;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.content.repository.TemplateVersionRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateVersionServiceTest {

    @Mock
    private EmailTemplateRepository templateRepository;

    @Mock
    private TemplateVersionRepository versionRepository;

    @Mock
    private ContentEventPublisher eventPublisher;

    private TemplateVersionService service;

    @BeforeEach
    void setUp() {
        service = new TemplateVersionService(templateRepository, versionRepository, eventPublisher);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publishVersion_publishesTemplateEventWithContextWorkspace() {
        EmailTemplate template = template();
        TemplateVersion version = version(template, 2, false);
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(versionRepository.findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId("template-1", 2, "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(version));

        service.publishVersion("template-1", 2);

        assertThat(version.getIsPublished()).isTrue();
        verify(versionRepository).clearPublishedVersionsExcept("template-1", "tenant-1", "workspace-1", 2);
        verify(eventPublisher).publishTemplatePublished(
                "tenant-1", "workspace-1", "template-1", "Template", "2");
    }

    @Test
    void publishVersion_publishesTemplateEventWithExplicitWorkspace() {
        EmailTemplate template = template();
        template.setWorkspaceId("workspace-2");
        TemplateVersion version = version(template, 3, false);
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-2"))
                .thenReturn(Optional.of(template));
        when(versionRepository.findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId("template-1", 3, "tenant-1", "workspace-2"))
                .thenReturn(Optional.of(version));

        service.publishVersion("tenant-1", "workspace-2", "template-1", 3);

        assertThat(version.getIsPublished()).isTrue();
        verify(versionRepository).clearPublishedVersionsExcept("template-1", "tenant-1", "workspace-2", 3);
        verify(eventPublisher).publishTemplatePublished(
                "tenant-1", "workspace-2", "template-1", "Template", "3");
    }

    @Test
    void listVersions_usesDefaultFirstPageRequestWithinTenantWorkspace() {
        EmailTemplate template = template();
        TemplateVersion version = version(template, 4, false);
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(versionRepository.findByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
                eq("template-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                any(Pageable.class)))
                .thenReturn(List.of(version));

        List<TemplateVersion> versions = service.listVersions("template-1");

        assertThat(versions).containsExactly(version);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(versionRepository).findByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
                eq("template-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void listVersions_clampsOversizedLimitToMaxFirstPageRequest() {
        EmailTemplate template = template();
        when(templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("template-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(template));
        when(versionRepository.findByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
                eq("template-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                any(Pageable.class)))
                .thenReturn(List.of());

        service.listVersions("template-1", 500);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(versionRepository).findByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(
                eq("template-1"),
                eq("tenant-1"),
                eq("workspace-1"),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(200);
    }

    private EmailTemplate template() {
        EmailTemplate template = new EmailTemplate();
        template.setId("template-1");
        template.setTenantId("tenant-1");
        template.setWorkspaceId("workspace-1");
        template.setName("Template");
        template.setSubject("Subject");
        template.setHtmlContent("<p>Hello</p>");
        template.setTextContent("Hello");
        template.setStatus(EmailTemplate.TemplateStatus.APPROVED);
        return template;
    }

    private TemplateVersion version(EmailTemplate template, int versionNumber, boolean published) {
        TemplateVersion version = new TemplateVersion();
        version.setTenantId(template.getTenantId());
        version.setWorkspaceId(template.getWorkspaceId());
        version.setTemplate(template);
        version.setVersionNumber(versionNumber);
        version.setSubject("Subject v" + versionNumber);
        version.setHtmlContent("<p>Hello v" + versionNumber + "</p>");
        version.setTextContent("Hello v" + versionNumber);
        version.setIsPublished(published);
        return version;
    }
}
