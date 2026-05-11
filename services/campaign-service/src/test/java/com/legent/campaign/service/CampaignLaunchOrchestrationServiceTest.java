package com.legent.campaign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.CampaignLaunchPlan;
import com.legent.campaign.domain.CampaignLaunchStep;
import com.legent.campaign.dto.CampaignEngineDto;
import com.legent.campaign.dto.CampaignLaunchDto;
import com.legent.campaign.repository.CampaignLaunchPlanRepository;
import com.legent.campaign.repository.CampaignLaunchStepRepository;
import com.legent.campaign.repository.CampaignRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignLaunchOrchestrationServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignLaunchPlanRepository launchPlanRepository;
    @Mock private CampaignLaunchStepRepository launchStepRepository;
    @Mock private CampaignEngineService campaignEngineService;
    @Mock private CampaignWorkflowService workflowService;
    @Mock private CampaignService campaignService;
    @Mock private OrchestrationService orchestrationService;

    private CampaignLaunchOrchestrationService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setRequestId("request-1");
        service = new CampaignLaunchOrchestrationService(
                campaignRepository,
                launchPlanRepository,
                launchStepRepository,
                campaignEngineService,
                workflowService,
                campaignService,
                orchestrationService,
                new ObjectMapper()
        );
        lenient().when(launchPlanRepository.findByTenantIdAndWorkspaceIdAndIdempotencyKeyAndDeletedAtIsNull(
                anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        lenient().when(launchPlanRepository.save(any(CampaignLaunchPlan.class))).thenAnswer(invocation -> {
            CampaignLaunchPlan plan = invocation.getArgument(0);
            if (plan.getId() == null) {
                plan.setId("plan-1");
            }
            if (plan.getCreatedAt() == null) {
                plan.setCreatedAt(Instant.now());
            }
            if (plan.getUpdatedAt() == null) {
                plan.setUpdatedAt(Instant.now());
            }
            return plan;
        });
        lenient().when(launchStepRepository.findByTenantIdAndWorkspaceIdAndLaunchPlanIdAndDeletedAtIsNullOrderBySortOrderAsc(
                anyString(), anyString(), anyString())).thenReturn(List.of());
        lenient().when(launchStepRepository.save(any(CampaignLaunchStep.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(campaignEngineService.preflight(anyString())).thenReturn(CampaignEngineDto.SendPreflightReport.builder()
                .campaignId("campaign-1")
                .sendAllowed(true)
                .errors(List.of())
                .warnings(List.of())
                .checks(Map.of())
                .build());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void previewBlocksIncompleteCampaign() {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setTenantId("tenant-1");
        campaign.setWorkspaceId("workspace-1");
        campaign.setName("Missing pieces");
        campaign.setApprovalRequired(true);

        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "campaign-1"))
                .thenReturn(Optional.of(campaign));

        CampaignLaunchDto.LaunchPlanResponse response = service.preview(request(CampaignLaunchDto.LaunchAction.PREVIEW));

        assertThat(response.getReadinessScore()).isLessThan(100);
        assertThat(response.getBlockerCount()).isGreaterThan(0);
        assertThat(response.getPrimaryAction()).isEqualTo("FIX_BLOCKERS");
        assertThat(response.getSteps()).anySatisfy(step -> {
            assertThat(step.getKey()).isEqualTo("audience");
            assertThat(step.getStatus()).isEqualTo("BLOCKED");
        });
    }

    @Test
    void safeFixEnablesNonDestructiveCampaignDefaults() {
        Campaign campaign = completeCampaign();
        campaign.setTrackingEnabled(false);
        campaign.setComplianceEnabled(false);
        campaign.setTimezone(null);

        when(campaignRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull("tenant-1", "workspace-1", "campaign-1"))
                .thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CampaignLaunchDto.LaunchPlanResponse response = service.execute(request(CampaignLaunchDto.LaunchAction.SAFE_FIX));

        assertThat(response.getStatus()).isEqualTo("EXECUTED");
        assertThat(campaign.getTrackingEnabled()).isTrue();
        assertThat(campaign.getComplianceEnabled()).isTrue();
        assertThat(campaign.getTimezone()).isEqualTo("UTC");
        verify(campaignRepository).save(campaign);
    }

    private CampaignLaunchDto.LaunchPlanRequest request(CampaignLaunchDto.LaunchAction action) {
        return CampaignLaunchDto.LaunchPlanRequest.builder()
                .campaignId("campaign-1")
                .idempotencyKey("ik-1")
                .action(action)
                .build();
    }

    private Campaign completeCampaign() {
        Campaign campaign = new Campaign();
        campaign.setId("campaign-1");
        campaign.setTenantId("tenant-1");
        campaign.setWorkspaceId("workspace-1");
        campaign.setName("Ready campaign");
        campaign.setSubject("Launch subject");
        campaign.setContentId("template-1");
        campaign.setSenderEmail("marketing@example.com");
        campaign.setSendingDomain("example.com");
        campaign.setProviderId("provider-1");
        campaign.setApprovalRequired(false);
        campaign.addAudience("LIST", "list-1");
        return campaign;
    }
}
