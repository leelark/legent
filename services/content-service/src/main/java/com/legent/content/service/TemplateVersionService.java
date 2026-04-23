package com.legent.content.service;

import com.legent.common.exception.NotFoundException;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.TemplateVersionDto;
import com.legent.content.event.ContentEventPublisher;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.content.repository.TemplateVersionRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TemplateVersionService {

    private final EmailTemplateRepository templateRepository;
    private final TemplateVersionRepository versionRepository;
    private final ContentEventPublisher eventPublisher;

    @Transactional
    public TemplateVersion createVersion(String templateId, TemplateVersionDto.Create request) {
        String tenantId = TenantContext.requireTenantId();
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(templateId, tenantId)
                .orElseThrow(() -> new NotFoundException("Template not found"));

        int nextVersion = versionRepository.countByTemplate_IdAndTenantId(templateId, tenantId) + 1;

        TemplateVersion version = new TemplateVersion();
        version.setTenantId(tenantId);
        version.setTemplate(template);
        version.setVersionNumber(nextVersion);
        version.setSubject(request.getSubject());
        version.setHtmlContent(request.getHtmlContent());
        version.setTextContent(request.getTextContent());
        version.setChanges(request.getChanges());
        version.setIsPublished(Boolean.TRUE.equals(request.getPublish()));

        version = versionRepository.save(version);

        if (Boolean.TRUE.equals(request.getPublish())) {
            publishTemplateVersion(template, version);
        }

        return version;
    }

    @Transactional
    public TemplateVersion publishVersion(String templateId, Integer versionNumber) {
        String tenantId = TenantContext.requireTenantId();
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(templateId, tenantId)
                .orElseThrow(() -> new NotFoundException("Template not found"));

        TemplateVersion version = versionRepository.findByTemplate_IdAndVersionNumberAndTenantId(templateId, versionNumber, tenantId)
                .orElseThrow(() -> new NotFoundException("Template version not found"));

        version.setIsPublished(true);
        versionRepository.save(version);
        return publishTemplateVersion(template, version);
    }

    private TemplateVersion publishTemplateVersion(EmailTemplate template, TemplateVersion version) {
        template.setSubject(version.getSubject());
        template.setHtmlContent(version.getHtmlContent());
        template.setTextContent(version.getTextContent());
        template.setStatus(EmailTemplate.TemplateStatus.PUBLISHED);
        templateRepository.save(template);

        eventPublisher.publishTemplatePublished(
                template.getTenantId(),
                template.getId(),
                template.getName(),
                String.valueOf(version.getVersionNumber())
        );
        return version;
    }
}
