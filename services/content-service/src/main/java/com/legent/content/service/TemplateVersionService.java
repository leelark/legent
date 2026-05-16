package com.legent.content.service;

import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
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

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TemplateVersionService {

    private final EmailTemplateRepository templateRepository;
    private final TemplateVersionRepository versionRepository;
    private final ContentEventPublisher eventPublisher;

    @Transactional
    public TemplateVersion createVersion(String templateId, TemplateVersionDto.Create request) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template not found"));

        int nextVersion = nextVersionNumber(templateId, tenantId, workspaceId);

        TemplateVersion version = new TemplateVersion();
        version.setTenantId(tenantId);
        version.setWorkspaceId(workspaceId);
        version.setTemplate(template);
        version.setVersionNumber(nextVersion);
        version.setSubject(request.getSubject() != null ? request.getSubject() : template.getSubject());
        version.setHtmlContent(request.getHtmlContent() != null ? request.getHtmlContent() : template.getHtmlContent());
        version.setTextContent(request.getTextContent() != null ? request.getTextContent() : template.getTextContent());
        version.setChanges(request.getChanges());
        if (Boolean.TRUE.equals(request.getPublish())) {
            throw new ValidationException("publish", "Use the approval-aware publish workflow instead of publishing during version creation");
        }
        version.setIsPublished(false);

        version = versionRepository.save(version);

        return version;
    }

    @Transactional
    public TemplateVersion publishVersion(String templateId, Integer versionNumber) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template not found"));

        TemplateVersion version = versionRepository.findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId(templateId, versionNumber, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template version not found"));

        version.setIsPublished(true);
        versionRepository.save(version);
        return publishTemplateVersion(template, version, tenantId, workspaceId);
    }

    private TemplateVersion publishTemplateVersion(EmailTemplate template, TemplateVersion version, String tenantId, String workspaceId) {
        List<TemplateVersion> allVersions = versionRepository.findByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(template.getId(), tenantId, workspaceId);
        for (TemplateVersion current : allVersions) {
            boolean shouldBePublished = Objects.equals(current.getVersionNumber(), version.getVersionNumber());
            if (!Objects.equals(current.getIsPublished(), shouldBePublished)) {
                current.setIsPublished(shouldBePublished);
                versionRepository.save(current);
            }
        }

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

    @Transactional(readOnly = true)
    public TemplateVersion getLatestVersion(String templateId) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return versionRepository.findFirstByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("No versions found for template: " + templateId));
    }

    @Transactional(readOnly = true)
    public TemplateVersion getLatestPublishedVersion(String templateId) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        return versionRepository.findFirstByTemplate_IdAndTenantIdAndWorkspaceIdAndIsPublishedTrueOrderByVersionNumberDesc(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Published template version not found"));
    }

    @Transactional(readOnly = true)
    public List<TemplateVersion> listVersions(String templateId) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template not found"));
        return versionRepository.findByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(templateId, tenantId, workspaceId);
    }

    @Transactional
    public TemplateVersion rollbackVersion(String templateId, Integer versionNumber, String reason, boolean publish) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template not found"));
        TemplateVersion sourceVersion = versionRepository.findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId(templateId, versionNumber, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template version not found"));

        TemplateVersion rollbackVersion = new TemplateVersion();
        rollbackVersion.setTenantId(tenantId);
        rollbackVersion.setWorkspaceId(workspaceId);
        rollbackVersion.setTemplate(template);
        rollbackVersion.setVersionNumber(nextVersionNumber(templateId, tenantId, workspaceId));
        rollbackVersion.setSubject(sourceVersion.getSubject());
        rollbackVersion.setHtmlContent(sourceVersion.getHtmlContent());
        rollbackVersion.setTextContent(sourceVersion.getTextContent());
        rollbackVersion.setChanges(reason != null && !reason.isBlank()
                ? reason
                : "Rollback from version " + versionNumber);
        if (publish) {
            throw new ValidationException("publish", "Rollback creates a draft version; use the approval-aware publish workflow after approval");
        }
        rollbackVersion.setIsPublished(false);

        rollbackVersion = versionRepository.save(rollbackVersion);
        return rollbackVersion;
    }

    @Transactional(readOnly = true)
    public TemplateVersionDto.CompareResponse compareVersions(String templateId, Integer leftVersionNumber, Integer rightVersionNumber) {
        String tenantId = TenantContext.requireTenantId();
        String workspaceId = TenantContext.requireWorkspaceId();
        TemplateVersion left = versionRepository.findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId(templateId, leftVersionNumber, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template version not found: " + leftVersionNumber));
        TemplateVersion right = versionRepository.findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId(templateId, rightVersionNumber, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template version not found: " + rightVersionNumber));

        TemplateVersionDto.CompareResponse response = new TemplateVersionDto.CompareResponse();
        response.setLeftVersion(leftVersionNumber);
        response.setRightVersion(rightVersionNumber);
        response.setLeftSubject(left.getSubject());
        response.setRightSubject(right.getSubject());
        response.setSubjectChanged(!Objects.equals(left.getSubject(), right.getSubject()));
        response.setHtmlChanged(!Objects.equals(left.getHtmlContent(), right.getHtmlContent()));
        response.setTextChanged(!Objects.equals(left.getTextContent(), right.getTextContent()));
        response.setLeftHtmlLength(lengthOf(left.getHtmlContent()));
        response.setRightHtmlLength(lengthOf(right.getHtmlContent()));
        response.setLeftTextLength(lengthOf(left.getTextContent()));
        response.setRightTextLength(lengthOf(right.getTextContent()));
        return response;
    }

    private int nextVersionNumber(String templateId, String tenantId, String workspaceId) {
        return versionRepository.findFirstByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(templateId, tenantId, workspaceId)
                .map(TemplateVersion::getVersionNumber)
                .orElse(0) + 1;
    }

    private Integer lengthOf(String value) {
        return value == null ? 0 : value.length();
    }
}
