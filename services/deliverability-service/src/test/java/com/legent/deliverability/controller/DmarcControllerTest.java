package com.legent.deliverability.controller;

import com.legent.common.constant.AppConstants;
import com.legent.deliverability.domain.DmarcReport;
import com.legent.deliverability.repository.DmarcReportRepository;
import com.legent.deliverability.service.DmarcReportService;
import com.legent.security.TenantContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DmarcControllerTest {

    @Mock private DmarcReportRepository reportRepository;
    @Mock private DmarcReportService reportService;

    private DmarcController controller;

    @BeforeEach
    void setUp() {
        controller = new DmarcController(reportRepository, reportService);
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void reportListUsesTenantWorkspaceScopedDefaultFirstPage() {
        DmarcReport report = report("example.com");
        when(reportRepository.findByTenantIdAndWorkspaceIdAndDomain(
                "tenant-1",
                "workspace-1",
                "example.com",
                PageRequest.of(0, AppConstants.DEFAULT_PAGE_SIZE)))
                .thenReturn(List.of(report));

        List<DmarcReport> response = controller.getReports("example.com", null);

        assertThat(response).containsExactly(report);
        verify(reportRepository).findByTenantIdAndWorkspaceIdAndDomain(
                "tenant-1",
                "workspace-1",
                "example.com",
                PageRequest.of(0, AppConstants.DEFAULT_PAGE_SIZE));
    }

    @Test
    void reportListCapsRequestedLimitToMaxFirstPage() {
        when(reportRepository.findByTenantIdAndWorkspaceIdAndDomain(
                "tenant-1",
                "workspace-1",
                "example.com",
                PageRequest.of(0, AppConstants.MAX_PAGE_SIZE)))
                .thenReturn(List.of());

        List<DmarcReport> response = controller.getReports("example.com", AppConstants.MAX_PAGE_SIZE + 1);

        assertThat(response).isEmpty();
        verify(reportRepository).findByTenantIdAndWorkspaceIdAndDomain(
                "tenant-1",
                "workspace-1",
                "example.com",
                PageRequest.of(0, AppConstants.MAX_PAGE_SIZE));
    }

    private static DmarcReport report(String domain) {
        DmarcReport report = new DmarcReport();
        report.setTenantId("tenant-1");
        report.setWorkspaceId("workspace-1");
        report.setDomain(domain);
        report.setReportType("AGGREGATE");
        return report;
    }
}
