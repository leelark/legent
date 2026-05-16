package com.legent.content.service;

import java.time.Instant;
import java.util.List;

import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.TemplateApproval;
import com.legent.content.domain.TemplateVersion;
import com.legent.content.dto.TemplateVersionDto;
import com.legent.content.event.ContentEventPublisher;
import com.legent.content.repository.EmailTemplateRepository;
import com.legent.content.repository.TemplateApprovalRepository;
import com.legent.content.repository.TemplateVersionRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for template approval workflow management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateWorkflowService {

    private final EmailTemplateRepository templateRepository;
    private final TemplateApprovalRepository approvalRepository;
    private final TemplateVersionRepository versionRepository;
    private final TemplateVersionService versionService;
    private final EmailRenderService renderService;
    private final ContentEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Value("${legent.content.approval-required-by-default:true}")
    private boolean approvalRequiredByDefault = true;

    /**
     * Submit a template for approval.
     */
    @Transactional
    public TemplateApproval submitForApproval(String tenantId, String templateId, String comments) {
        return submitForApproval(tenantId, TenantContext.requireWorkspaceId(), templateId, comments);
    }

    @Transactional
    public TemplateApproval submitForApproval(String tenantId, String workspaceId, String templateId, String comments) {
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template", templateId));
        String userId = resolveActor(TenantContext.getUserId(), template.getCreatedBy(), "system");

        // Check if there's already a pending approval
        if (approvalRepository.findPendingApproval(tenantId, workspaceId, templateId).isPresent()) {
            throw new ConflictException("Template already has a pending approval request");
        }

        // Check if template is in a valid state for approval
        if (template.getStatus() != EmailTemplate.TemplateStatus.DRAFT &&
            template.getStatus() != EmailTemplate.TemplateStatus.APPROVED) {
            throw new ValidationException("status", "Template must be in DRAFT or APPROVED state to submit for approval");
        }

        // Get the next version number
        int nextVersion = versionRepository.findFirstByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(templateId, tenantId, workspaceId)
                .map(TemplateVersion::getVersionNumber)
                .orElse(0) + 1;

        // Snapshot current draft/content for review and future publish.
        TemplateVersion snapshot = new TemplateVersion();
        snapshot.setTenantId(tenantId);
        snapshot.setWorkspaceId(workspaceId);
        snapshot.setTemplate(template);
        snapshot.setVersionNumber(nextVersion);
        snapshot.setSubject(template.getDraftSubject() != null ? template.getDraftSubject() : template.getSubject());
        snapshot.setHtmlContent(template.getDraftHtmlContent() != null ? template.getDraftHtmlContent() : template.getHtmlContent());
        snapshot.setTextContent(template.getDraftTextContent() != null ? template.getDraftTextContent() : template.getTextContent());
        snapshot.setChanges("Submitted for approval");
        snapshot.setIsPublished(false);
        versionRepository.save(snapshot);

        // Create approval request
        TemplateApproval approval = new TemplateApproval();
        approval.setTenantId(tenantId);
        approval.setWorkspaceId(workspaceId);
        approval.setTemplateId(templateId);
        approval.setVersionNumber(nextVersion);
        approval.setRequestedBy(userId);
        approval.setCreatedBy(userId);
        approval.setStatus(TemplateApproval.ApprovalStatus.PENDING);
        approval.setComments(comments);

        // Update template status
        template.setStatus(EmailTemplate.TemplateStatus.PENDING_APPROVAL);
        template.setCurrentApprover(null); // Will be set when someone claims it

        approval = approvalRepository.saveAndFlush(approval);
        templateRepository.save(template);

        log.info("Template submitted for approval: tenant={}, template={}, version={}",
                tenantId, templateId, nextVersion);

        eventPublisher.publishTemplateSubmittedForApproval(tenantId, templateId, template.getName(), nextVersion);

        return approvalRepository.findById(approval.getId()).orElse(approval);
    }

    /**
     * Approve a template.
     */
    @Transactional
    public TemplateApproval approveTemplate(String tenantId, String approvalId, String comments) {
        return approveTemplate(tenantId, TenantContext.requireWorkspaceId(), approvalId, comments);
    }

    @Transactional
    public TemplateApproval approveTemplate(String tenantId, String workspaceId, String approvalId, String comments) {
        TemplateApproval approval = approvalRepository.findByIdAndTenantIdAndWorkspaceId(approvalId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("TemplateApproval", approvalId));

        if (approval.getStatus() != TemplateApproval.ApprovalStatus.PENDING) {
            throw new ValidationException("status", "Approval is not in PENDING state");
        }

        String templateIdFromApproval = approval.getTemplateId();
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateIdFromApproval, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template", templateIdFromApproval));
        String userId = resolveActor(TenantContext.getUserId(), approval.getRequestedBy(), template.getCreatedBy(), "system");

        // Update approval
        approval.setStatus(TemplateApproval.ApprovalStatus.APPROVED);
        approval.setApprovedBy(userId);
        approval.setApprovedAt(Instant.now());
        if (comments != null && !comments.isBlank()) {
            approval.setComments(comments);
        }

        // Update template status
        template.setStatus(EmailTemplate.TemplateStatus.APPROVED);
        template.setCurrentApprover(userId);

        approval = approvalRepository.saveAndFlush(approval);
        templateRepository.save(template);

        log.info("Template approved: tenant={}, template={}, version={}, approver={}",
                tenantId, template.getId(), approval.getVersionNumber(), userId);

        eventPublisher.publishTemplateApproved(tenantId, template.getId(), template.getName(),
                approval.getVersionNumber(), userId);

        return approvalRepository.findById(approval.getId()).orElse(approval);
    }

    /**
     * Reject a template approval.
     */
    @Transactional
    public TemplateApproval rejectTemplate(String tenantId, String approvalId, String reason) {
        return rejectTemplate(tenantId, TenantContext.requireWorkspaceId(), approvalId, reason);
    }

    @Transactional
    public TemplateApproval rejectTemplate(String tenantId, String workspaceId, String approvalId, String reason) {
        TemplateApproval approval = approvalRepository.findByIdAndTenantIdAndWorkspaceId(approvalId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("TemplateApproval", approvalId));

        if (approval.getStatus() != TemplateApproval.ApprovalStatus.PENDING) {
            throw new ValidationException("status", "Approval is not in PENDING state");
        }

        String templateIdFromApproval = approval.getTemplateId();
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateIdFromApproval, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template", templateIdFromApproval));
        String userId = resolveActor(TenantContext.getUserId(), approval.getRequestedBy(), template.getCreatedBy(), "system");

        // Update approval
        approval.setStatus(TemplateApproval.ApprovalStatus.REJECTED);
        approval.setApprovedBy(userId);
        approval.setApprovedAt(Instant.now());
        approval.setRejectionReason(reason);

        // Revert template to DRAFT status
        template.setStatus(EmailTemplate.TemplateStatus.DRAFT);
        template.setCurrentApprover(userId);

        approval = approvalRepository.saveAndFlush(approval);
        templateRepository.save(template);

        log.info("Template rejected: tenant={}, template={}, version={}, reason={}",
                tenantId, template.getId(), approval.getVersionNumber(), reason);

        eventPublisher.publishTemplateRejected(tenantId, template.getId(), template.getName(),
                approval.getVersionNumber(), reason, userId);

        return approvalRepository.findById(approval.getId()).orElse(approval);
    }

    /**
     * Publish an approved template.
     */
    @Transactional
    public TemplateVersion publishTemplate(String tenantId, String templateId, Integer versionNumber) {
        return publishTemplate(tenantId, TenantContext.requireWorkspaceId(), templateId, versionNumber, false, null);
    }

    /**
     * Publish an approved template, with an auditable admin bypass for controlled bootstrap or emergency use.
     */
    @Transactional
    public TemplateVersion publishTemplate(String tenantId, String templateId, Integer versionNumber,
                                           boolean adminBypass, String bypassReason) {
        return publishTemplate(tenantId, TenantContext.requireWorkspaceId(), templateId, versionNumber, adminBypass, bypassReason);
    }

    @Transactional
    public TemplateVersion publishTemplate(String tenantId, String workspaceId, String templateId, Integer versionNumber,
                                           boolean adminBypass, String bypassReason) {
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template", templateId));
        String userId = resolveActor(TenantContext.getUserId(), template.getCurrentApprover(), template.getCreatedBy(), "system");

        TemplateVersion version;
        if (versionNumber != null) {
            // Publish specific version
            version = versionRepository.findByTemplate_IdAndVersionNumberAndTenantIdAndWorkspaceId(templateId, versionNumber, tenantId, workspaceId)
                    .orElseThrow(() -> new NotFoundException("TemplateVersion", templateId + " v" + versionNumber));
        } else {
            // Get the latest version
            version = versionRepository.findFirstByTemplate_IdAndTenantIdAndWorkspaceIdOrderByVersionNumberDesc(templateId, tenantId, workspaceId)
                    .orElseGet(() -> {
                        TemplateVersionDto.Create initialVersion = new TemplateVersionDto.Create();
                        initialVersion.setSubject(template.getDraftSubject() != null ? template.getDraftSubject() : template.getSubject());
                        initialVersion.setHtmlContent(template.getDraftHtmlContent() != null ? template.getDraftHtmlContent() : template.getHtmlContent());
                        initialVersion.setTextContent(template.getDraftTextContent() != null ? template.getDraftTextContent() : template.getTextContent());
                        initialVersion.setChanges("Initial publish snapshot");
                        initialVersion.setPublish(false);
                        return versionService.createVersion(templateId, initialVersion);
                    });
        }

        boolean approvalRequired = approvalRequiredByDefault || template.isApprovalRequired();
        boolean approvedForVersion = template.getStatus() == EmailTemplate.TemplateStatus.APPROVED
                && approvalRepository.hasApprovedApproval(tenantId, workspaceId, templateId, version.getVersionNumber());
        if (approvalRequired && !approvedForVersion) {
            if (!adminBypass) {
                throw new ValidationException("status", "Template version must be approved before publishing");
            }
            requireAdminBypassAllowed(bypassReason);
            log.warn("Template publish admin bypass: tenant={}, template={}, version={}, actor={}, reason={}",
                    tenantId, templateId, version.getVersionNumber(), userId, bypassReason);
        }

        renderService.requirePublishable(tenantId, workspaceId, templateId, version.getVersionNumber());

        // Publish the version
        versionService.publishVersion(templateId, version.getVersionNumber());

        // Update template metadata
        template.setStatus(EmailTemplate.TemplateStatus.PUBLISHED);
        template.setLastPublishedVersion(version.getVersionNumber());
        template.setLastPublishedAt(Instant.now());
        template.setCurrentApprover(userId);
        template.setDraftSubject(null);
        template.setDraftHtmlContent(null);
        template.setDraftTextContent(null);
        templateRepository.save(template);

        log.info("Template published: tenant={}, template={}, version={}",
                tenantId, templateId, version.getVersionNumber());

        return version;
    }

    /**
     * Save draft content without publishing.
     */
    @Transactional
    public EmailTemplate saveDraft(String tenantId, String templateId, String subject,
                                    String htmlContent, String textContent) {
        return saveDraft(tenantId, TenantContext.requireWorkspaceId(), templateId, subject, htmlContent, textContent);
    }

    @Transactional
    public EmailTemplate saveDraft(String tenantId, String workspaceId, String templateId, String subject,
                                    String htmlContent, String textContent) {
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template", templateId));

        // Only allow draft updates for certain statuses
        if (template.getStatus() == EmailTemplate.TemplateStatus.PUBLISHED) {
            // For published templates, create a new draft version
            template.setDraftSubject(subject);
            template.setDraftHtmlContent(htmlContent);
            template.setDraftTextContent(textContent);
            // Keep status as PUBLISHED, but draft content is available
        } else {
            // For draft templates, update the main content
            if (subject != null) template.setSubject(subject);
            if (htmlContent != null) template.setHtmlContent(htmlContent);
            if (textContent != null) template.setTextContent(textContent);
            template.setStatus(EmailTemplate.TemplateStatus.DRAFT);
        }

        templateRepository.save(template);

        log.info("Template draft saved: tenant={}, template={}", tenantId, templateId);

        return template;
    }

    /**
     * Get pending approvals for a tenant.
     */
    @Transactional(readOnly = true)
    public List<TemplateApproval> getPendingApprovals(String tenantId) {
        return getPendingApprovals(tenantId, TenantContext.requireWorkspaceId());
    }

    @Transactional(readOnly = true)
    public List<TemplateApproval> getPendingApprovals(String tenantId, String workspaceId) {
        return approvalRepository.findByTenantIdAndWorkspaceIdAndStatus(tenantId, workspaceId, TemplateApproval.ApprovalStatus.PENDING);
    }

    /**
     * Get approval history for a template.
     */
    @Transactional(readOnly = true)
    public List<TemplateApproval> getTemplateApprovalHistory(String tenantId, String templateId) {
        return getTemplateApprovalHistory(tenantId, TenantContext.requireWorkspaceId(), templateId);
    }

    @Transactional(readOnly = true)
    public List<TemplateApproval> getTemplateApprovalHistory(String tenantId, String workspaceId, String templateId) {
        templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template", templateId));
        return approvalRepository.findByTenantIdAndWorkspaceIdAndTemplateIdOrderByRequestedAtDesc(tenantId, workspaceId, templateId);
    }

    /**
     * Cancel a pending approval request.
     */
    @Transactional
    public TemplateApproval cancelApproval(String tenantId, String approvalId) {
        return cancelApproval(tenantId, TenantContext.requireWorkspaceId(), approvalId);
    }

    @Transactional
    public TemplateApproval cancelApproval(String tenantId, String workspaceId, String approvalId) {
        TemplateApproval approval = approvalRepository.findByIdAndTenantIdAndWorkspaceId(approvalId, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("TemplateApproval", approvalId));
        String userId = resolveActor(TenantContext.getUserId(), approval.getRequestedBy(), "system");

        if (approval.getStatus() != TemplateApproval.ApprovalStatus.PENDING) {
            throw new ValidationException("status", "Can only cancel PENDING approvals");
        }

        // Only the requester can cancel
        if (!approval.getRequestedBy().equals(userId)) {
            throw new ValidationException("user", "Only the requester can cancel this approval");
        }

        String templateIdFromApproval = approval.getTemplateId();
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(templateIdFromApproval, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("Template", templateIdFromApproval));

        approval.setStatus(TemplateApproval.ApprovalStatus.CANCELLED);
        template.setStatus(EmailTemplate.TemplateStatus.DRAFT);

        approval = approvalRepository.saveAndFlush(approval);
        templateRepository.save(template);

        log.info("Template approval cancelled: tenant={}, template={}", tenantId, template.getId());

        return approvalRepository.findById(approval.getId()).orElse(approval);
    }

    private void requireAdminBypassAllowed(String bypassReason) {
        if (bypassReason == null || bypassReason.isBlank()) {
            throw new ValidationException("bypassReason", "Admin bypass requires a reason");
        }
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            throw new ValidationException("adminBypass", "Admin bypass requires an authenticated admin");
        }
        boolean allowed = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN")
                        || authority.equals("ROLE_SUPER_ADMIN")
                        || authority.equals("ROLE_CONTENT_ADMIN")
                        || authority.equals("CONTENT_APPROVE")
                        || authority.equals("content:approve"));
        if (!allowed) {
            throw new ValidationException("adminBypass", "Admin bypass requires content approval permission");
        }
    }

    private String resolveActor(String... candidates) {
        if (candidates == null) {
            return "system";
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "system";
    }
}
