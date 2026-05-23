package com.legent.audience.controller;

import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.service.DataExtensionQueryActivityService;
import com.legent.audience.service.DataExtensionService;
import com.legent.common.constant.AppConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataExtensionControllerTest {

    @Mock
    private DataExtensionService deService;

    @Mock
    private DataExtensionQueryActivityService queryActivityService;

    private DataExtensionController controller;

    @BeforeEach
    void setUp() {
        controller = new DataExtensionController(deService, queryActivityService);
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
}
