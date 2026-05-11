package com.legent.automation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.domain.AutomationActivity;
import com.legent.automation.domain.AutomationActivityRun;
import com.legent.automation.dto.AutomationStudioDto;
import com.legent.automation.repository.AutomationActivityRepository;
import com.legent.automation.repository.AutomationActivityRunRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationStudioServiceTest {

    @Mock private AutomationActivityRepository activityRepository;
    @Mock private AutomationActivityRunRepository runRepository;

    private AutomationStudioService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        service = new AutomationStudioService(activityRepository, runRepository, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createSqlActivityPersistsVerificationWarnings() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Daily SQL"))
                .thenReturn(false);
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> {
            AutomationActivity activity = invocation.getArgument(0);
            activity.setId("activity-1");
            return activity;
        });

        AutomationStudioDto.ActivityResponse response = service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Daily SQL")
                .activityType(AutomationStudioDto.ActivityType.SQL_QUERY)
                .inputConfig(Map.of("sql", "SELECT email FROM subscribers"))
                .outputConfig(Map.of())
                .build());

        assertThat(response.getVerification()).containsKey("warnings");
        assertThat(response.getActivityType()).isEqualTo(AutomationStudioDto.ActivityType.SQL_QUERY);
    }

    @Test
    void dryRunRecordsFailedRunForUnsafeWebhook() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Webhook");
        activity.setActivityType(AutomationStudioDto.ActivityType.WEBHOOK);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"url\":\"http://example.com\"}");
        activity.setOutputConfig("{}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(true).triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.FAILED);
        assertThat(response.getErrorMessage()).contains("https");
    }
}
