package com.legent.automation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.client.AudienceDataExtensionClient;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationStudioServiceTest {

    @Mock private AutomationActivityRepository activityRepository;
    @Mock private AutomationActivityRunRepository runRepository;
    @Mock private AudienceDataExtensionClient audienceDataExtensionClient;

    private AutomationStudioService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        service = new AutomationStudioService(activityRepository, runRepository, new ObjectMapper(), audienceDataExtensionClient);
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
    void createUnsupportedActivityWithValidShapeIsNotVerifiedAsExecutable() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Webhook"))
                .thenReturn(false);
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> {
            AutomationActivity activity = invocation.getArgument(0);
            activity.setId("activity-1");
            return activity;
        });

        AutomationStudioDto.ActivityResponse response = service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Webhook")
                .activityType(AutomationStudioDto.ActivityType.WEBHOOK)
                .inputConfig(Map.of("url", "https://example.com/hook", "method", "POST"))
                .outputConfig(Map.of())
                .build());

        assertThat(response.getVerification()).containsEntry("valid", false);
        assertThat(response.getVerification().get("errors").toString()).contains("WEBHOOK activity execution is not supported");
        assertThat(response.getVerification().get("normalizedConfig").toString()).contains("liveExecutionSupported=false");
    }

    @Test
    void createUnsupportedActiveActivityFailsBeforePersistence() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Webhook"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Webhook")
                .activityType(AutomationStudioDto.ActivityType.WEBHOOK)
                .status(AutomationStudioDto.ActivityStatus.ACTIVE)
                .inputConfig(Map.of("url", "https://example.com/hook", "method", "POST"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("ACTIVE automation activities require verification.valid=true and liveExecutionSupported=true")
                .hasMessageContaining("WEBHOOK activity execution is not supported");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
    }

    @Test
    void updateActiveActivityFailsWhenVerificationBecomesInvalid() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Daily SQL");
        activity.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"sql\":\"SELECT email FROM customers\"}");
        activity.setOutputConfig("{\"targetDataExtensionId\":\"de-target\"}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Webhook"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.updateActivity("activity-1", AutomationStudioDto.ActivityRequest.builder()
                .name("Webhook")
                .activityType(AutomationStudioDto.ActivityType.WEBHOOK)
                .status(AutomationStudioDto.ActivityStatus.ACTIVE)
                .inputConfig(Map.of("url", "https://example.com/hook", "method", "POST"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("ACTIVE automation activities require verification.valid=true and liveExecutionSupported=true")
                .hasMessageContaining("WEBHOOK activity execution is not supported");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
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

    @Test
    void sqlActivityDryRunCallsAudienceExecutorAndRecordsRows() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Daily SQL");
        activity.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"sql\":\"SELECT email FROM customers WHERE score >= 10\",\"maxRows\":100}");
        activity.setOutputConfig("{\"targetDataExtensionId\":\"de-target\",\"writeMode\":\"OVERWRITE\"}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(audienceDataExtensionClient.runSqlQueryActivity(eq("tenant-1"), eq("workspace-1"), any()))
                .thenReturn(Map.of(
                        "valid", true,
                        "rowsRead", 25L,
                        "rowsWritten", 0L,
                        "dryRun", true));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(true).triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.VERIFIED);
        assertThat(response.getRowsRead()).isEqualTo(25L);
        assertThat(response.getRowsWritten()).isZero();
        verify(audienceDataExtensionClient).runSqlQueryActivity(eq("tenant-1"), eq("workspace-1"), any());
    }

    @Test
    void sqlActivityDependencyFailureIsRecordedAsFailedRun() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Daily SQL");
        activity.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"sql\":\"SELECT email FROM customers\"}");
        activity.setOutputConfig("{\"targetDataExtensionId\":\"de-target\"}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(audienceDataExtensionClient.runSqlQueryActivity(eq("tenant-1"), eq("workspace-1"), any()))
                .thenThrow(new IllegalStateException("audience-service unavailable"));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(true).triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.FAILED);
        assertThat(response.getErrorMessage()).contains("audience-service unavailable");
    }

    @Test
    void emptyRunRequestDefaultsToDryRun() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Subscriber Import");
        activity.setActivityType(AutomationStudioDto.ActivityType.IMPORT);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"sourceLocation\":\"import_123.csv\",\"targetType\":\"SUBSCRIBER\",\"fieldMapping\":{\"email\":\"Email Address\"}}");
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
                AutomationStudioDto.RunRequest.builder().triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.VERIFIED);
        assertThat(response.isDryRun()).isTrue();
        verify(audienceDataExtensionClient, never()).startImportActivity(any(), any(), any());
    }

    @Test
    void liveRunRequiresExplicitConfirmation() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Subscriber Import");
        activity.setActivityType(AutomationStudioDto.ActivityType.IMPORT);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"sourceLocation\":\"import_123.csv\",\"targetType\":\"SUBSCRIBER\",\"fieldMapping\":{\"email\":\"Email Address\"}}");
        activity.setOutputConfig("{}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).triggerSource("TEST").build()))
                .hasMessageContaining("confirmLiveRun=true");

        verify(runRepository, never()).save(any(AutomationActivityRun.class));
        verify(audienceDataExtensionClient, never()).startImportActivity(any(), any(), any());
    }

    @Test
    void importActivityLiveRunStartsAudienceImportJob() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Subscriber Import");
        activity.setActivityType(AutomationStudioDto.ActivityType.IMPORT);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"sourceLocation\":\"import_123.csv\",\"targetType\":\"SUBSCRIBER\",\"fieldMapping\":{\"email\":\"Email Address\"}}");
        activity.setOutputConfig("{}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(audienceDataExtensionClient.startImportActivity(eq("tenant-1"), eq("workspace-1"), any()))
                .thenReturn(Map.of("id", "import-1", "status", "PENDING", "targetType", "SUBSCRIBER"));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("importJobId", "import-1");
        verify(audienceDataExtensionClient).startImportActivity(eq("tenant-1"), eq("workspace-1"), any());
    }

    @Test
    void importActivityLiveRunStartsDataExtensionImportJob() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Data Extension Import");
        activity.setActivityType(AutomationStudioDto.ActivityType.IMPORT);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"sourceLocation\":\"import_123.csv\",\"targetType\":\"DATA_EXTENSION\",\"targetId\":\"de-1\",\"fieldMapping\":{\"email\":\"Email\",\"score\":\"Score\"}}");
        activity.setOutputConfig("{}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(audienceDataExtensionClient.startImportActivity(eq("tenant-1"), eq("workspace-1"), any()))
                .thenReturn(Map.of("id", "import-2", "status", "PENDING", "targetType", "DATA_EXTENSION"));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("importJobId", "import-2");
        verify(audienceDataExtensionClient).startImportActivity(eq("tenant-1"), eq("workspace-1"), any());
    }

    @Test
    void unsupportedLiveActivityRecordsFailedRunInsteadOfSyntheticSuccess() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Extract");
        activity.setActivityType(AutomationStudioDto.ActivityType.EXTRACT);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"sourceType\":\"DATA_EXTENSION\"}");
        activity.setOutputConfig("{\"destination\":\"extracts/daily.csv\"}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.FAILED);
        assertThat(response.getErrorMessage()).contains("EXTRACT activity execution is not supported");
        verify(audienceDataExtensionClient, never()).runSqlQueryActivity(any(), any(), any());
        verify(audienceDataExtensionClient, never()).startImportActivity(any(), any(), any());
    }
}
