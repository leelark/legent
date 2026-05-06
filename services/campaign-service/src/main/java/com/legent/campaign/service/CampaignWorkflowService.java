package com.legent.campaign.service;

import java.time.Instant;
import java.util.List;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignApproval;
import com.legent.campaign.repository.CampaignApprovalRepository;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for campaign approval workflow management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignWorkflowService {

    private final CampaignRepository campaignRepository;
    private final CampaignApprovalRepository approvalRepository;
    private final CampaignStateMachineService stateMachine;

    /**
     * Submit a campaign for approval.
     */
    @Transactional
    public CampaignApproval submitForApproval(String tenantId, String campaignId, String comments) {
        String userId = TenantContext.getUserId();
        String workspaceId = TenantContext.requireWorkspaceId();

        Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));

        // Check if there's already a pending approval
        if (approvalRepository.hasPendingApproval(tenantId, campaignId)) {
            throw new ConflictException("Campaign already has a pending approval request");
        }

        // Check if campaign is in a valid state for approval
        if (campaign.getStatus() != Campaign.CampaignStatus.DRAFT
                && campaign.getStatus() != Campaign.CampaignStatus.APPROVED) {
            throw new ValidationException("status", "Campaign must be in DRAFT or APPROVED state to submit for approval");
        }

        // Create approval request
        CampaignApproval approval = new CampaignApproval();
        approval.setTenantId(tenantId);
        approval.setCampaignId(campaignId);
        approval.setRequestedBy(userId);
        approval.setStatus(CampaignApproval.ApprovalStatus.PENDING);
        approval.setComments(comments);

        approvalRepository.save(approval);
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.REVIEW_PENDING, comments);
        campaign.setCurrentApprover(null);
        campaignRepository.save(campaign);

        log.info("Campaign submitted for approval: tenant={}, campaign={}", tenantId, campaignId);

        return approval;
    }

    /**
     * Approve a campaign.
     */
    @Transactional
    public CampaignApproval approveCampaign(String tenantId, String approvalId, String comments) {
        String userId = TenantContext.getUserId();
        String workspaceId = TenantContext.requireWorkspaceId();

        CampaignApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("CampaignApproval", approvalId));

        if (!approval.getTenantId().equals(tenantId)) {
            throw new ValidationException("tenant", "Approval does not belong to this tenant");
        }

        if (approval.getStatus() != CampaignApproval.ApprovalStatus.PENDING) {
            throw new ValidationException("status", "Approval is not in PENDING state");
        }

        Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, approval.getCampaignId())
                .orElseThrow(() -> new NotFoundException("Campaign", approval.getCampaignId()));

        // Update approval
        approval.setStatus(CampaignApproval.ApprovalStatus.APPROVED);
        approval.setApprovedBy(userId);
        approval.setApprovedAt(Instant.now());
        approval.setComments(comments);

        // Update campaign status
        campaign.setApprovedBy(userId);
        campaign.setApprovedAt(Instant.now());
        campaign.setCurrentApprover(userId);
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.APPROVED, comments);

        approvalRepository.save(approval);
        campaignRepository.save(campaign);

        log.info("Campaign approved: tenant={}, campaign={}, approver={}",
                tenantId, campaign.getId(), userId);

        return approval;
    }

    /**
     * Reject a campaign approval.
     */
    @Transactional
    public CampaignApproval rejectCampaign(String tenantId, String approvalId, String reason, String comments) {
        String userId = TenantContext.getUserId();
        String workspaceId = TenantContext.requireWorkspaceId();

        CampaignApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("CampaignApproval", approvalId));

        if (!approval.getTenantId().equals(tenantId)) {
            throw new ValidationException("tenant", "Approval does not belong to this tenant");
        }

        if (approval.getStatus() != CampaignApproval.ApprovalStatus.PENDING) {
            throw new ValidationException("status", "Approval is not in PENDING state");
        }

        Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, approval.getCampaignId())
                .orElseThrow(() -> new NotFoundException("Campaign", approval.getCampaignId()));

        // Update approval
        approval.setStatus(CampaignApproval.ApprovalStatus.REJECTED);
        approval.setApprovedBy(userId);
        approval.setApprovedAt(Instant.now());
        approval.setRejectionReason(reason);
        // LEGENT-MED-002: Persist comments field that was previously ignored
        approval.setComments(comments);

        approvalRepository.save(approval);

        // Keep campaign in DRAFT state for editing
        campaign.setCurrentApprover(userId);
        stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.DRAFT, reason != null ? reason : comments);
        campaignRepository.save(campaign);

        log.info("Campaign rejected: tenant={}, campaign={}, reason={}",
                tenantId, campaign.getId(), reason);

        return approval;
    }

    /**
     * Get pending approvals for a tenant.
     */
    @Transactional(readOnly = true)
    public List<CampaignApproval> getPendingApprovals(String tenantId) {
        String workspaceId = TenantContext.requireWorkspaceId();
        return approvalRepository.findByTenantIdAndStatus(tenantId, CampaignApproval.ApprovalStatus.PENDING).stream()
                .filter(approval -> campaignRepository
                        .findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, approval.getCampaignId())
                        .isPresent())
                .toList();
    }

    /**
     * Get approval history for a campaign.
     */
    @Transactional(readOnly = true)
    public List<CampaignApproval> getCampaignApprovalHistory(String tenantId, String campaignId) {
        String workspaceId = TenantContext.requireWorkspaceId();
        campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign", campaignId));
        return approvalRepository.findByTenantIdAndCampaignIdOrderByRequestedAtDesc(tenantId, campaignId);
    }

    /**
     * Cancel a pending approval request.
     */
    @Transactional
    public CampaignApproval cancelApproval(String tenantId, String approvalId) {
        String userId = TenantContext.getUserId();
        String workspaceId = TenantContext.requireWorkspaceId();

        CampaignApproval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NotFoundException("CampaignApproval", approvalId));

        if (!approval.getTenantId().equals(tenantId)) {
            throw new ValidationException("tenant", "Approval does not belong to this tenant");
        }

        if (approval.getStatus() != CampaignApproval.ApprovalStatus.PENDING) {
            throw new ValidationException("status", "Can only cancel PENDING approvals");
        }

        // Only the requester can cancel
        if (!approval.getRequestedBy().equals(userId)) {
            throw new ValidationException("user", "Only the requester can cancel this approval");
        }

        Campaign campaign = campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, approval.getCampaignId())
                .orElseThrow(() -> new NotFoundException("Campaign", approval.getCampaignId()));

        approval.setStatus(CampaignApproval.ApprovalStatus.CANCELLED);
        approvalRepository.save(approval);
        if (campaign.getStatus() == Campaign.CampaignStatus.REVIEW_PENDING) {
            stateMachine.transitionCampaign(campaign, Campaign.CampaignStatus.DRAFT, "Approval cancelled");
            campaignRepository.save(campaign);
        }

        log.info("Campaign approval cancelled: tenant={}, campaign={}", tenantId, approval.getCampaignId());

        return approval;
    }
}
