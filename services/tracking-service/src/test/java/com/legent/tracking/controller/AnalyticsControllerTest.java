package com.legent.tracking.controller;

import com.legent.common.dto.ApiResponse;
import com.legent.security.TenantContext;
import com.legent.tracking.domain.CampaignSummary;
import com.legent.tracking.dto.TrackingDto;
import com.legent.tracking.repository.CampaignSummaryRepository;
import com.legent.tracking.service.AnalyticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsControllerTest {

    private CampaignSummaryRepository campaignSummaryRepository;
    private AnalyticsService analyticsService;
    private AnalyticsController controller;

    @BeforeEach
    void setUp() {
        campaignSummaryRepository = mock(CampaignSummaryRepository.class);
        analyticsService = mock(AnalyticsService.class);
        controller = new AnalyticsController(campaignSummaryRepository, analyticsService);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getAllCampaignSummariesUsesDefaultBoundedFirstPageAndPreservesListResponseShape() {
        CampaignSummary summary = summary("campaign-1");
        PageRequest page = PageRequest.of(0, AnalyticsController.DEFAULT_CAMPAIGN_SUMMARY_LIMIT);
        when(campaignSummaryRepository.findAllByTenantIdAndWorkspaceId("tenant-1", "workspace-1", page))
                .thenReturn(List.of(summary));

        ApiResponse<List<CampaignSummary>> response = controller.getAllCampaignSummaries(null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).containsExactly(summary);
        assertThat(response.getMeta()).isNotNull();
        verify(campaignSummaryRepository).findAllByTenantIdAndWorkspaceId("tenant-1", "workspace-1", page);
    }

    @Test
    void getAllCampaignSummariesClampsInvalidLimitToDefaultFirstPage() {
        PageRequest page = PageRequest.of(0, AnalyticsController.DEFAULT_CAMPAIGN_SUMMARY_LIMIT);
        when(campaignSummaryRepository.findAllByTenantIdAndWorkspaceId("tenant-1", "workspace-1", page))
                .thenReturn(List.of());

        ApiResponse<List<CampaignSummary>> response = controller.getAllCampaignSummaries(0);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEmpty();
        verify(campaignSummaryRepository).findAllByTenantIdAndWorkspaceId("tenant-1", "workspace-1", page);
    }

    @Test
    void getAllCampaignSummariesClampsExcessiveLimitToMaxFirstPage() {
        PageRequest page = PageRequest.of(0, AnalyticsController.MAX_CAMPAIGN_SUMMARY_LIMIT);
        when(campaignSummaryRepository.findAllByTenantIdAndWorkspaceId("tenant-1", "workspace-1", page))
                .thenReturn(List.of());

        ApiResponse<List<CampaignSummary>> response = controller.getAllCampaignSummaries(
                AnalyticsController.MAX_CAMPAIGN_SUMMARY_LIMIT + 1_000);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEmpty();
        verify(campaignSummaryRepository).findAllByTenantIdAndWorkspaceId("tenant-1", "workspace-1", page);
    }

    @Test
    void getEventTimelinePassesExplicitWindowAndBucketLimitToService() {
        Instant startAt = Instant.parse("2026-05-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-05-02T00:00:00Z");
        List<Map<String, Object>> rows = List.of(Map.of("hour", startAt, "count", 7L));
        when(analyticsService.getEventTimeline("tenant-1", "workspace-1", "open", startAt, endAt, 24))
                .thenReturn(rows);

        ApiResponse<List<Map<String, Object>>> response = controller.getEventTimeline("open", startAt, endAt, 24);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(rows);
        verify(analyticsService).getEventTimeline("tenant-1", "workspace-1", "open", startAt, endAt, 24);
    }

    @Test
    void getRollupsPassesExplicitWindowAndBucketLimitWithoutChangingDtoShape() {
        Instant startAt = Instant.parse("2026-05-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-05-08T00:00:00Z");
        List<Map<String, Object>> rows = List.of(Map.of("campaign_id", "campaign-1", "opens", 3L));
        when(analyticsService.getRollups("tenant-1", "workspace-1", "campaign-1", "day", startAt, endAt, 7))
                .thenReturn(rows);

        ApiResponse<TrackingDto.RollupResponse> response = controller.getRollups(
                "campaign-1", "day", startAt, endAt, 7);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getCampaignId()).isEqualTo("campaign-1");
        assertThat(response.getData().getGrain()).isEqualTo("day");
        assertThat(response.getData().getRows()).isEqualTo(rows);
        verify(analyticsService).getRollups("tenant-1", "workspace-1", "campaign-1", "day", startAt, endAt, 7);
    }

    private static CampaignSummary summary(String campaignId) {
        CampaignSummary summary = new CampaignSummary();
        summary.setTenantId("tenant-1");
        summary.setWorkspaceId("workspace-1");
        summary.setCampaignId(campaignId);
        summary.setTotalSends(10L);
        summary.setTotalOpens(4L);
        return summary;
    }
}
