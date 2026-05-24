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
import java.util.Optional;

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
    void getEventCountsPassesExplicitWindowToService() {
        Instant startAt = Instant.parse("2026-05-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-05-02T00:00:00Z");
        List<Map<String, Object>> rows = List.of(Map.of("event_type", "OPEN", "count", 7L));
        when(analyticsService.getEventCounts("tenant-1", "workspace-1", startAt, endAt))
                .thenReturn(rows);

        ApiResponse<List<Map<String, Object>>> response = controller.getEventCounts(startAt, endAt);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo(rows);
        verify(analyticsService).getEventCounts("tenant-1", "workspace-1", startAt, endAt);
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
        assertThat(response.getData().getQuerySemantics()).isEqualTo(AnalyticsService.QUERY_SEMANTICS_CANONICAL_EVENT_ID);
        assertThat(response.getData().getDedupeKey()).containsExactlyElementsOf(AnalyticsService.CANONICAL_DEDUPE_KEY);
        assertThat(response.getData().getRows()).isEqualTo(rows);
        verify(analyticsService).getRollups("tenant-1", "workspace-1", "campaign-1", "day", startAt, endAt, 7);
    }

    @Test
    void exportEventsKeepsPhysicalRawRowsAndExposesSemanticsMetadata() {
        TrackingDto.EventExportRequest request = TrackingDto.EventExportRequest.builder()
                .campaignId("campaign-1")
                .eventTypes(List.of("OPEN"))
                .limit(20_000)
                .format("json")
                .build();
        List<Map<String, Object>> rows = List.of(Map.of("id", "event-1", "event_type", "OPEN"));
        when(analyticsService.exportEvents("tenant-1", "workspace-1", "campaign-1", List.of("OPEN"), null, null, 20_000))
                .thenReturn(rows);

        ApiResponse<TrackingDto.EventExportResponse> response = controller.exportEvents(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getFormat()).isEqualTo("JSON");
        assertThat(response.getData().getRowCount()).isEqualTo(1);
        assertThat(response.getData().getMetadata())
                .containsEntry("cappedLimit", 10_000)
                .containsEntry("sourceDataset", AnalyticsService.SOURCE_DATASET_RAW_EVENTS)
                .containsEntry("querySemantics", AnalyticsService.QUERY_SEMANTICS_PHYSICAL_RAW_ROW)
                .containsEntry("canonicalOperationalDefault", AnalyticsService.QUERY_SEMANTICS_CANONICAL_EVENT_ID);
        verify(analyticsService).exportEvents("tenant-1", "workspace-1", "campaign-1", List.of("OPEN"), null, null, 20_000);
    }

    @Test
    void reconcileCampaignComparesSummaryCountsToCanonicalCountsAndKeepsRawDiagnostics() {
        CampaignSummary summary = summary("campaign-1");
        when(campaignSummaryRepository.findByTenantIdAndWorkspaceIdAndCampaignId("tenant-1", "workspace-1", "campaign-1"))
                .thenReturn(Optional.of(summary));
        when(analyticsService.canonicalCountsForCampaign("tenant-1", "workspace-1", "campaign-1"))
                .thenReturn(Map.of(
                        "SEND", 10L,
                        "OPEN", 4L,
                        "CLICK", 0L,
                        "CONVERSION", 0L,
                        "BOUNCE", 0L));
        when(analyticsService.rawPhysicalCountsForCampaign("tenant-1", "workspace-1", "campaign-1"))
                .thenReturn(Map.of("SEND", 10L, "OPEN", 5L));

        ApiResponse<TrackingDto.ReconciliationResponse> response = controller.reconcileCampaign("campaign-1");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().isReconciled()).isTrue();
        assertThat(response.getData().getCanonicalEventCounts()).containsEntry("OPEN", 4L);
        assertThat(response.getData().getRawEventCounts()).containsEntry("OPEN", 5L);
        assertThat(response.getData().getQuerySemantics()).isEqualTo(AnalyticsService.QUERY_SEMANTICS_CANONICAL_EVENT_ID);
        assertThat(response.getData().getDedupeKey()).containsExactlyElementsOf(AnalyticsService.CANONICAL_DEDUPE_KEY);
    }

    private static CampaignSummary summary(String campaignId) {
        CampaignSummary summary = new CampaignSummary();
        summary.setTenantId("tenant-1");
        summary.setWorkspaceId("workspace-1");
        summary.setCampaignId(campaignId);
        summary.setTotalSends(10L);
        summary.setTotalOpens(4L);
        summary.setTotalClicks(0L);
        summary.setTotalConversions(0L);
        summary.setTotalBounces(0L);
        return summary;
    }
}
