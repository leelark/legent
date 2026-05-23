package com.legent.automation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.domain.WorkflowSchedule;
import com.legent.automation.repository.WorkflowRepository;
import com.legent.automation.repository.WorkflowScheduleRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowScheduleServiceTest {

    @Mock private WorkflowScheduleRepository workflowScheduleRepository;
    @Mock private WorkflowRepository workflowRepository;
    @Mock private Scheduler scheduler;

    private WorkflowScheduleService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        service = new WorkflowScheduleService(
                workflowScheduleRepository,
                workflowRepository,
                scheduler,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listSchedulesUsesDefaultFirstPage() {
        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setId("schedule-1");
        schedule.setTenantId("tenant-1");
        schedule.setWorkspaceId("workspace-1");
        schedule.setWorkflowId("workflow-1");
        when(workflowScheduleRepository.findByTenantIdAndWorkspaceIdAndWorkflowIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), eq("workflow-1"), any(Pageable.class)))
                .thenReturn(List.of(schedule));

        List<WorkflowSchedule> schedules = service.listSchedules("workflow-1");

        assertThat(schedules).containsExactly(schedule);
        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(workflowScheduleRepository).findByTenantIdAndWorkspaceIdAndWorkflowIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), eq("workflow-1"), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(50);
    }
}
