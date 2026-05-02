package com.legent.content.service;

import java.time.Instant;
import java.util.List;

import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.EmailTemplate;
import com.legent.content.domain.TemplateApproval;
import com.legent.content.domain.TemplateVersion;
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
    private final ContentEventPublisher eventPublisher;

    /**
     * Submit a template for approval.
     */
    @Transactional
    public TemplateApproval submitForApproval(String tenantId, String templateId, String comments) {
        String userId = TenantContext.getUserId();

        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(templateId, tenantId)
                .orElseThrow(() -> new NotFoundException("Template", templateId));

        // Check if there's already a pending approval
        if (approvalRepository.findPendingApproval(tenantId, templateId).isPresent()) {
            throw new ConflictException("Template already has a pending approval request");
        }

        // Check if template is in a valid state for approval
        if (template.getStatus() != EmailTemplate.TemplateStatus.DRAFT &&
            template.getStatus() != EmailTemplate.TemplateStatus.APPROVED) {
            throw new ValidationException("status", "Template must be in DRAFT or APPROVED state to submit for approval");
        }

        // Get the next version number
        int nextVersion = versionRepository.countByTemplate_IdAndTenantId(templateId, tenantId) + 1;

        // Create approval request
        TemplateApproval approval = new TemplateApproval();
        approval.setTenantId(tenantId);
        approval.setTemplateId(templateId);
        approval.setVersionNumber(nextVersion);
        approval.setRequestedBy(userId);
        approval.setStatus(TemplateApproval.ApprovalStatus.PENDING);
        approval.setComments(comments);

        // Update template status
        template.setStatus(EmailTemplate.TemplateStatus.PENDING_APPROVAL);
        template.setCurrentApprover(null); // Will be set when someone claims it

        approvalRepository.save(approval);
        templateRepository.save(template);

        log.info("Template submitted for approval: tenant={}, template={}, version={}",
                tenantId, templateId, nextVersion);

        eventPublisher.publishTemplateSubmittedForApproval(tenantId, templateId, template.getName(), nextVersion);

        return approval;
    }

    /**
     * Approve a template.
     */
    @Transactional
    public TemplateApproval approveTemplate(String tenantId, String approvalId, String comments) {
        String userId = TenantContext.getUserId();

        TemplateApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("TemplateApproval", approvalId));

        if (!approval.getTenantId().equals(tenantId)) {
            throw new ValidationException("tenant", "Approval does not belong to this tenant");
        }

        if (approval.getStatus() != TemplateApproval.ApprovalStatus.PENDING) {
            throw new ValidationException("status", "Approval is not in PENDING state");
        }

        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(approval.getTemplateId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Template", approval.getTemplateId()));

        // Update approval
        approval.setStatus(TemplateApproval.ApprovalStatus.APPROVED);
        approval.setApprovedBy(userId);
        approval.setApprovedAt(Instant.now());
        approval.setComments(comments);

        // Update template status
        template.setStatus(EmailTemplate.TemplateStatus.APPROVED);
        template.setCurrentApprover(userId);

        approvalRepository.save(approval);
        templateRepository.save(template);

        log.info("Template approved: tenant={}, template={}, version={}, approver={}",
                tenantId, template.getId(), approval.getVersionNumber(), userId);

        eventPublisher.publishTemplateApproved(tenantId, template.getId(), template.getName(),
                approval.getVersionNumber(), userId);

        return approval;
    }

    /**
     * Reject a template approval.
     */
    @Transactional
    public TemplateApproval rejectTemplate(String tenantId, String approvalId, String reason) {
        String userId = TenantContext.getUserId();

        TemplateApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("TemplateApproval", approvalId));

        if (!approval.getTenantId().equals(tenantId)) {
            throw new ValidationException("tenant", "Approval does not belong to this tenant");
        }

        if (approval.getStatus() != TemplateApproval.ApprovalStatus.PENDING) {
            throw new ValidationException("status", "Approval is not in PENDING state");
        }

        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(approval.getTemplateId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Template", approval.getTemplateId()));

        // Update approval
        approval.setStatus(TemplateApproval.ApprovalStatus.REJECTED);
        approval.setApprovedBy(userId);
        approval.setApprovedAt(Instant.now());
        approval.setRejectionReason(reason);

        // Revert template to DRAFT status
        template.setStatus(EmailTemplate.TemplateStatus.DRAFT);
        template.setCurrentApprover(userId);

        approvalRepository.save(approval);
        templateRepository.save(template);

        log.info("Template rejected: tenant={}, template={}, version={}, reason={}",
                tenantId, template.getId(), approval.getVersionNumber(), reason);

        eventPublisher.publishTemplateRejected(tenantId, template.getId(), template.getName(),
                approval.getVersionNumber(), reason, userId);

        return approval;
    }

    /**
     * Publish an approved template.
     */
    @Transactional
    public TemplateVersion publishTemplate(String tenantId, String templateId, Integer versionNumber) {
        String userId = TenantContext.getUserId();

        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(templateId, tenantId)
                .orElseThrow(() -> new NotFoundException("Template", templateId));

        // Check if approval is required and template is approved
        if (template.isApprovalRequired() && template.getStatus() != EmailTemplate.TemplateStatus.APPROVED) {
            throw new ValidationException("status", "Template must be approved before publishing");
        }

        TemplateVersion version;
        if (versionNumber != null) {
            // Publish specific version
            version = versionRepository.findByTemplate_IdAndVersionNumberAndTenantId(templateId, versionNumber, tenantId)
                    .orElseThrow(() -> new NotFoundException("TemplateVersion", templateId + " v" + versionNumber));
        } else {
            // Get the latest version
            version = versionRepository.findFirstByTemplate_IdAndTenantIdOrderByVersionNumberDesc(templateId, tenantId)
                    .orElseThrow(() -> new NotFoundException("No versions found for template"));
        }

        // Publish the version
        versionService.publishVersion(templateId, version.getVersionNumber());

        // Update template metadata
        template.setStatus(EmailTemplate.TemplateStatus.PUBLISHED);
        template.setLastPublishedVersion(version.getVersionNumber());
        template.setLastPublishedAt(Instant.now());
        template.setCurrentApprover(userId);
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
        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(templateId, tenantId)
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
        return approvalRepository.findByTenantIdAndStatus(tenantId, TemplateApproval.ApprovalStatus.PENDING);
    }

    /**
     * Get approval history for a template.
     */
    @Transactional(readOnly = true)
    public List<TemplateApproval> getTemplateApprovalHistory(String tenantId, String templateId) {
        return approvalRepository.findByTenantIdAndTemplateIdOrderByRequestedAtDesc(tenantId, templateId);
    }

    /**
     * Cancel a pending approval request.
     */
    @Transactional
    public TemplateApproval cancelApproval(String tenantId, String approvalId) {
        String userId = TenantContext.getUserId();

        TemplateApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("TemplateApproval", approvalId));

        if (!approval.getTenantId().equals(tenantId)) {
            throw new ValidationException("tenant", "Approval does not belong to this tenant");
        }

        if (approval.getStatus() != TemplateApproval.ApprovalStatus.PENDING) {
            throw new ValidationException("status", "Can only cancel PENDING approvals");
        }

        // Only the requester can cancel
        if (!approval.getRequestedBy().equals(userId)) {
            throw new ValidationException("user", "Only the requester can cancel this approval");
        }

        EmailTemplate template = templateRepository.findByIdAndTenantIdAndDeletedAtIsNull(approval.getTemplateId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Template", approval.getTemplateId()));

        approval.setStatus(TemplateApproval.ApprovalStatus.CANCELLED);
        template.setStatus(EmailTemplate.TemplateStatus.DRAFT);

        approvalRepository.save(approval);
        templateRepository.save(template);

        log.info("Template approval cancelled: tenant={}, template={}", tenantId, template.getId());

        return approval;
    }
}
