package com.legent.campaign.service;

import java.util.Optional;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignApproval;
import com.legent.campaign.repository.CampaignApprovalRepository;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignWorkflowServiceTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String USER_ID = "user-1";
    private static final String CAMPAIGN_ID = "campaign-1";
    private static final String APPROVAL_ID = "approval-1";

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignApprovalRepository approvalRepository;
    @Mock private CampaignStateMachineService stateMachine;
    @Mock private CampaignLockService campaignLockService;

    @InjectMocks private CampaignWorkflowService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
        TenantContext.setUserId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void approveCampaignFailsClosedWhenApprovalIsOutsideCurrentWorkspace() {
        when(approvalRepository.findByTenantIdAndOwningCampaignWorkspaceIdAndId(
                TENANT_ID, WORKSPACE_ID, APPROVAL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approveCampaign(TENANT_ID, APPROVAL_ID, "approved"))
                .isInstanceOf(NotFoundException.class);

        verifyNoMutationSideEffects();
    }

    @Test
    void rejectCampaignFailsClosedWhenApprovalIsOutsideCurrentWorkspace() {
        when(approvalRepository.findByTenantIdAndOwningCampaignWorkspaceIdAndId(
                TENANT_ID, WORKSPACE_ID, APPROVAL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rejectCampaign(TENANT_ID, APPROVAL_ID, "reason", "comments"))
                .isInstanceOf(NotFoundException.class);

        verifyNoMutationSideEffects();
    }

    @Test
    void cancelApprovalFailsClosedWhenApprovalIsOutsideCurrentWorkspace() {
        when(approvalRepository.findByTenantIdAndOwningCampaignWorkspaceIdAndId(
                TENANT_ID, WORKSPACE_ID, APPROVAL_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelApproval(TENANT_ID, APPROVAL_ID))
                .isInstanceOf(NotFoundException.class);

        verifyNoMutationSideEffects();
    }

    @Test
    void approveCampaignUsesTenantAndWorkspaceScopedApprovalLookupBeforeMutation() {
        CampaignApproval approval = pendingApproval();
        Campaign campaign = reviewPendingCampaign();
        when(approvalRepository.findByTenantIdAndOwningCampaignWorkspaceIdAndId(
                TENANT_ID, WORKSPACE_ID, APPROVAL_ID))
                .thenReturn(Optional.of(approval));
        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                TENANT_ID, WORKSPACE_ID, CAMPAIGN_ID))
                .thenReturn(Optional.of(campaign));

        CampaignApproval result = service.approveCampaign(TENANT_ID, APPROVAL_ID, "approved");

        assertThat(result.getStatus()).isEqualTo(CampaignApproval.ApprovalStatus.APPROVED);
        assertThat(result.getApprovedBy()).isEqualTo(USER_ID);
        verify(approvalRepository).save(approval);
        verify(campaignLockService).lockCampaign(campaign);
        verify(stateMachine).transitionCampaign(campaign, Campaign.CampaignStatus.APPROVED, "approved");
    }

    private CampaignApproval pendingApproval() {
        CampaignApproval approval = new CampaignApproval();
        approval.setId(APPROVAL_ID);
        approval.setTenantId(TENANT_ID);
        approval.setCampaignId(CAMPAIGN_ID);
        approval.setRequestedBy(USER_ID);
        approval.setStatus(CampaignApproval.ApprovalStatus.PENDING);
        return approval;
    }

    private Campaign reviewPendingCampaign() {
        Campaign campaign = new Campaign();
        campaign.setId(CAMPAIGN_ID);
        campaign.setTenantId(TENANT_ID);
        campaign.setWorkspaceId(WORKSPACE_ID);
        campaign.setStatus(Campaign.CampaignStatus.REVIEW_PENDING);
        return campaign;
    }

    private void verifyNoMutationSideEffects() {
        verify(approvalRepository, never()).save(any(CampaignApproval.class));
        verify(campaignRepository, never()).save(any(Campaign.class));
        verifyNoInteractions(stateMachine, campaignLockService);
    }
}
