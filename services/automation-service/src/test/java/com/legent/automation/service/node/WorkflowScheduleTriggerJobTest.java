package com.legent.automation.service.node;

import com.legent.automation.domain.WorkflowSchedule;
import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.automation.repository.WorkflowScheduleRepository;
import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationContext;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowScheduleTriggerJobTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String WORKSPACE_ID = "workspace-1";
    private static final String WORKFLOW_ID = "workflow-1";
    private static final String SCHEDULE_ID = "schedule-1";
    private static final Integer VERSION = 3;
    private static final Instant SCHEDULED_AT = Instant.parse("2026-05-22T08:30:00Z");
    private static final String REQUEST_ID = SCHEDULE_ID + ":" + SCHEDULED_AT.toEpochMilli();

    @Mock private ApplicationContext applicationContext;
    @Mock private WorkflowScheduleRepository scheduleRepository;
    @Mock private WorkflowEventPublisher eventPublisher;
    @Mock private JobExecutionContext jobExecutionContext;

    private WorkflowScheduleTriggerJob job;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        job = new WorkflowScheduleTriggerJob(applicationContext);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void executeInternalPublishesOnlyAfterValidatingActiveMatchingSchedule() throws Exception {
        when(jobExecutionContext.getJobDetail()).thenReturn(validJobDetail());
        when(jobExecutionContext.getScheduledFireTime()).thenReturn(Date.from(SCHEDULED_AT));
        when(applicationContext.getBean(WorkflowScheduleRepository.class)).thenReturn(scheduleRepository);
        when(scheduleRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                SCHEDULE_ID, TENANT_ID, WORKSPACE_ID)).thenAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(TenantContext.getWorkspaceId()).isNull();
            return Optional.of(activeSchedule(WORKFLOW_ID));
        });
        when(applicationContext.getBean(WorkflowEventPublisher.class)).thenReturn(eventPublisher);
        doAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(TenantContext.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
            assertThat(TenantContext.getRequestId()).isEqualTo(REQUEST_ID);
            assertThat(TenantContext.getCorrelationId()).isEqualTo(REQUEST_ID);
            return null;
        }).when(eventPublisher).publishAction(eq(AppConstants.TOPIC_WORKFLOW_TRIGGER), eq(TENANT_ID), eq(SCHEDULE_ID), any());

        job.executeInternal(jobExecutionContext);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publishAction(
                eq(AppConstants.TOPIC_WORKFLOW_TRIGGER),
                eq(TENANT_ID),
                eq(SCHEDULE_ID),
                payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload)
                .containsEntry("workflowId", WORKFLOW_ID)
                .containsEntry("version", VERSION)
                .containsEntry("subscriberId", "schedule:" + SCHEDULE_ID)
                .containsEntry("workspaceId", WORKSPACE_ID)
                .containsEntry("idempotencyKey", REQUEST_ID);
        assertThat(contextPayload(payload))
                .containsEntry("triggerSource", "SCHEDULED")
                .containsEntry("scheduleId", SCHEDULE_ID)
                .containsEntry("scheduledAt", SCHEDULED_AT.toString());
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    @Test
    void missingVersionPublishesWithNullVersionForEngineFallback() throws Exception {
        when(jobExecutionContext.getJobDetail()).thenReturn(jobDetailWithout("version"));
        when(jobExecutionContext.getScheduledFireTime()).thenReturn(Date.from(SCHEDULED_AT));
        when(applicationContext.getBean(WorkflowScheduleRepository.class)).thenReturn(scheduleRepository);
        when(scheduleRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                SCHEDULE_ID, TENANT_ID, WORKSPACE_ID)).thenReturn(Optional.of(activeSchedule(WORKFLOW_ID)));
        when(applicationContext.getBean(WorkflowEventPublisher.class)).thenReturn(eventPublisher);

        job.executeInternal(jobExecutionContext);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publishAction(
                eq(AppConstants.TOPIC_WORKFLOW_TRIGGER),
                eq(TENANT_ID),
                eq(SCHEDULE_ID),
                payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).containsEntry("version", null);
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedJobData")
    void malformedJobDataFailsClosedBeforeRepositoryLookup(String scenario, JobDetail jobDetail, Date scheduledFireTime)
            throws Exception {
        when(jobExecutionContext.getJobDetail()).thenReturn(jobDetail);
        if (jobDetail != null) {
            when(jobExecutionContext.getScheduledFireTime()).thenReturn(scheduledFireTime);
        }

        job.executeInternal(jobExecutionContext);

        verifyNoInteractions(applicationContext, scheduleRepository, eventPublisher);
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    @Test
    void missingScheduleFailsClosedWithoutPublisher() throws Exception {
        when(jobExecutionContext.getJobDetail()).thenReturn(validJobDetail());
        when(jobExecutionContext.getScheduledFireTime()).thenReturn(Date.from(SCHEDULED_AT));
        when(applicationContext.getBean(WorkflowScheduleRepository.class)).thenReturn(scheduleRepository);
        when(scheduleRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                SCHEDULE_ID, TENANT_ID, WORKSPACE_ID)).thenReturn(Optional.empty());

        job.executeInternal(jobExecutionContext);

        verify(applicationContext, never()).getBean(WorkflowEventPublisher.class);
        verifyNoInteractions(eventPublisher);
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("staleSchedules")
    void staleScheduleRowsFailClosedWithoutPublisher(String scenario, WorkflowSchedule schedule) throws Exception {
        when(jobExecutionContext.getJobDetail()).thenReturn(validJobDetail());
        when(jobExecutionContext.getScheduledFireTime()).thenReturn(Date.from(SCHEDULED_AT));
        when(applicationContext.getBean(WorkflowScheduleRepository.class)).thenReturn(scheduleRepository);
        when(scheduleRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                SCHEDULE_ID, TENANT_ID, WORKSPACE_ID)).thenAnswer(invocation -> {
            assertThat(TenantContext.getTenantId()).isNull();
            assertThat(TenantContext.getWorkspaceId()).isNull();
            return Optional.of(schedule);
        });

        job.executeInternal(jobExecutionContext);

        verify(applicationContext, never()).getBean(WorkflowEventPublisher.class);
        verifyNoInteractions(eventPublisher);
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    @Test
    void publisherFailurePropagatesAndClearsTenantContext() {
        RuntimeException failure = new IllegalStateException("kafka unavailable");
        when(jobExecutionContext.getJobDetail()).thenReturn(validJobDetail());
        when(jobExecutionContext.getScheduledFireTime()).thenReturn(Date.from(SCHEDULED_AT));
        when(applicationContext.getBean(WorkflowScheduleRepository.class)).thenReturn(scheduleRepository);
        when(scheduleRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                SCHEDULE_ID, TENANT_ID, WORKSPACE_ID)).thenReturn(Optional.of(activeSchedule(WORKFLOW_ID)));
        when(applicationContext.getBean(WorkflowEventPublisher.class)).thenReturn(eventPublisher);
        doThrow(failure).when(eventPublisher).publishAction(eq(AppConstants.TOPIC_WORKFLOW_TRIGGER), eq(TENANT_ID), eq(SCHEDULE_ID), any());

        assertThatThrownBy(() -> job.executeInternal(jobExecutionContext)).isSameAs(failure);

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    private static Stream<Arguments> malformedJobData() {
        return Stream.of(
                Arguments.of("missing job detail", null, Date.from(SCHEDULED_AT)),
                Arguments.of("missing tenantId", jobDetailWithout("tenantId"), Date.from(SCHEDULED_AT)),
                Arguments.of("blank workspaceId", jobDetailWith("workspaceId", " "), Date.from(SCHEDULED_AT)),
                Arguments.of("missing workflowId", jobDetailWithout("workflowId"), Date.from(SCHEDULED_AT)),
                Arguments.of("missing scheduleId", jobDetailWithout("scheduleId"), Date.from(SCHEDULED_AT)),
                Arguments.of("invalid version", jobDetailWith("version", "latest"), Date.from(SCHEDULED_AT)),
                Arguments.of("non-positive version", jobDetailWith("version", 0), Date.from(SCHEDULED_AT)),
                Arguments.of("missing scheduledFireTime", validJobDetail(), null));
    }

    private static Stream<Arguments> staleSchedules() {
        WorkflowSchedule inactive = activeSchedule(WORKFLOW_ID);
        inactive.setStatus("PAUSED");
        WorkflowSchedule mismatchedWorkflow = activeSchedule("workflow-2");
        WorkflowSchedule mismatchedTenant = activeSchedule(WORKFLOW_ID);
        mismatchedTenant.setTenantId("tenant-2");
        WorkflowSchedule mismatchedWorkspace = activeSchedule(WORKFLOW_ID);
        mismatchedWorkspace.setWorkspaceId("workspace-2");
        return Stream.of(
                Arguments.of("inactive schedule", inactive),
                Arguments.of("mismatched workflow", mismatchedWorkflow),
                Arguments.of("mismatched tenant", mismatchedTenant),
                Arguments.of("mismatched workspace", mismatchedWorkspace));
    }

    private static JobDetail validJobDetail() {
        return jobDetail(validDataMap());
    }

    private static JobDetail jobDetailWithout(String key) {
        JobDataMap dataMap = validDataMap();
        dataMap.remove(key);
        return jobDetail(dataMap);
    }

    private static JobDetail jobDetailWith(String key, Object value) {
        JobDataMap dataMap = validDataMap();
        dataMap.put(key, value);
        return jobDetail(dataMap);
    }

    private static JobDetail jobDetail(JobDataMap dataMap) {
        return JobBuilder.newJob(WorkflowScheduleTriggerJob.class)
                .withIdentity(SCHEDULE_ID, "workflow-schedules")
                .usingJobData(dataMap)
                .build();
    }

    private static JobDataMap validDataMap() {
        JobDataMap dataMap = new JobDataMap();
        dataMap.put("tenantId", TENANT_ID);
        dataMap.put("workspaceId", WORKSPACE_ID);
        dataMap.put("workflowId", WORKFLOW_ID);
        dataMap.put("scheduleId", SCHEDULE_ID);
        dataMap.put("version", VERSION);
        return dataMap;
    }

    private static WorkflowSchedule activeSchedule(String workflowId) {
        WorkflowSchedule schedule = new WorkflowSchedule();
        schedule.setId(SCHEDULE_ID);
        schedule.setTenantId(TENANT_ID);
        schedule.setWorkspaceId(WORKSPACE_ID);
        schedule.setWorkflowId(workflowId);
        schedule.setStatus("ACTIVE");
        return schedule;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> contextPayload(Map<String, Object> payload) {
        return (Map<String, Object>) payload.get("context");
    }
}
