package com.legent.audience.controller;

import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.service.DataExtensionQueryActivityService;
import com.legent.audience.service.DataExtensionService;
import com.legent.common.constant.AppConstants;
import com.legent.common.security.InternalServiceIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExtensionControllerTest {

    @Mock
    private DataExtensionService deService;

    @Mock
    private DataExtensionQueryActivityService queryActivityService;

    private DataExtensionController controller;
    private static final String INTERNAL_TOKEN = "internal-service-token-1234567890abcdef";

    @BeforeEach
    void setUp() {
        controller = new DataExtensionController(deService, queryActivityService);
        ReflectionTestUtils.setField(controller, "internalApiToken", INTERNAL_TOKEN);
    }

    @Test
    void listClampsInvalidPageAndSizeBeforeServiceCall() {
        PageRequest expectedPage = PageRequest.of(0, AppConstants.DEFAULT_PAGE_SIZE);
        when(deService.list(expectedPage)).thenReturn(new PageImpl<>(List.of(), expectedPage, 42));

        var response = controller.list(-5, 0);

        assertThat(response.getPagination().getPage()).isZero();
        assertThat(response.getPagination().getSize()).isEqualTo(AppConstants.DEFAULT_PAGE_SIZE);
        assertThat(response.getPagination().getTotalElements()).isEqualTo(42);
        verify(deService).list(expectedPage);
    }

    @Test
    void listClampsExcessiveSizeBeforeServiceCall() {
        PageRequest expectedPage = PageRequest.of(2, AppConstants.MAX_PAGE_SIZE);
        when(deService.list(expectedPage)).thenReturn(new PageImpl<>(List.of(), expectedPage, 0));

        var response = controller.list(2, AppConstants.MAX_PAGE_SIZE + 500);

        assertThat(response.getPagination().getPage()).isEqualTo(2);
        assertThat(response.getPagination().getSize()).isEqualTo(AppConstants.MAX_PAGE_SIZE);
        verify(deService).list(expectedPage);
    }

    @Test
    void listRecordsClampsInvalidPageAndExcessiveSizeBeforeServiceCall() {
        PageRequest expectedPage = PageRequest.of(0, AppConstants.MAX_PAGE_SIZE);
        when(deService.listRecords("de-1", expectedPage)).thenReturn(new PageImpl<>(List.of(), expectedPage, 7));

        var response = controller.listRecords("de-1", -1, AppConstants.MAX_PAGE_SIZE + 1);

        assertThat(response.getPagination().getPage()).isZero();
        assertThat(response.getPagination().getSize()).isEqualTo(AppConstants.MAX_PAGE_SIZE);
        assertThat(response.getPagination().getTotalElements()).isEqualTo(7);
        verify(deService).listRecords("de-1", expectedPage);
    }

    @Test
    void listRecordsClampsInvalidSizeToDefaultBeforeServiceCall() {
        PageRequest expectedPage = PageRequest.of(3, AppConstants.DEFAULT_PAGE_SIZE);
        when(deService.listRecords("de-1", expectedPage)).thenReturn(new PageImpl<>(List.of(), expectedPage, 0));

        var response = controller.listRecords("de-1", 3, -100);

        assertThat(response.getPagination().getPage()).isEqualTo(3);
        assertThat(response.getPagination().getSize()).isEqualTo(AppConstants.DEFAULT_PAGE_SIZE);
        verify(deService).listRecords("de-1", expectedPage);
    }

    @Test
    void runSqlQueryActivityRequiresSignedAutomationIdentity() {
        DataExtensionDto.SqlQueryActivityRequest request = DataExtensionDto.SqlQueryActivityRequest.builder()
                .sql("select email from subscribers")
                .dryRun(true)
                .build();
        DataExtensionDto.SqlQueryActivityResponse expected = DataExtensionDto.SqlQueryActivityResponse.builder()
                .valid(true)
                .dryRun(true)
                .build();
        when(queryActivityService.execute(same(request))).thenReturn(expected);
        Instant timestamp = Instant.now();

        var response = controller.runSqlQueryActivity(
                INTERNAL_TOKEN,
                "automation-service",
                timestamp.toString(),
                signature("tenant-1", "workspace-1", InternalServiceIdentity.ACTION_DATA_EXTENSION_QUERY_ACTIVITY, timestamp),
                "tenant-1",
                "workspace-1",
                request);

        assertThat(response.getData()).isSameAs(expected);
        verify(queryActivityService).execute(request);
    }

    @Test
    void runSqlQueryActivityRejectsUnsignedInternalTokenBeforeServiceAccess() {
        DataExtensionDto.SqlQueryActivityRequest request = DataExtensionDto.SqlQueryActivityRequest.builder()
                .sql("select email from subscribers")
                .build();

        assertThatThrownBy(() -> controller.runSqlQueryActivity(
                INTERNAL_TOKEN,
                "automation-service",
                null,
                null,
                "tenant-1",
                "workspace-1",
                request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(queryActivityService);
    }

    private String signature(String tenantId, String workspaceId, String action, Instant timestamp) {
        return InternalServiceIdentity.sign(INTERNAL_TOKEN, "automation-service", tenantId, workspaceId, action, timestamp);
    }
}
