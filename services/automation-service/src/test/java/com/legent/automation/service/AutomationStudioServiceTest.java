package com.legent.automation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.client.AudienceDataExtensionClient;
import com.legent.automation.domain.AutomationActivity;
import com.legent.automation.domain.AutomationActivityRun;
import com.legent.automation.domain.AutomationArtifact;
import com.legent.automation.dto.AutomationStudioDto;
import com.legent.automation.event.WorkflowEventPublisher;
import com.legent.automation.repository.AutomationActivityRepository;
import com.legent.automation.repository.AutomationActivityRunRepository;
import com.legent.common.constant.AppConstants;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
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
    @Mock private AutomationArtifactService artifactService;
    @Mock private AutomationEventIdempotencyService idempotencyService;
    @Mock private WorkflowEventPublisher workflowEventPublisher;

    private AutomationStudioService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        service = new AutomationStudioService(activityRepository, runRepository, new ObjectMapper(), audienceDataExtensionClient,
                artifactService, idempotencyService, workflowEventPublisher);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AutomationArtifact stubImportArtifact(String artifactId) {
        AutomationArtifact artifact = artifact(artifactId, AutomationArtifact.ArtifactStatus.READY, AutomationArtifact.SourceKind.UPLOAD);
        when(artifactService.requireImportArtifact(artifactId)).thenReturn(artifact);
        when(artifactService.summary(artifact)).thenReturn(artifactSummary(artifactId));
        return artifact;
    }

    private AutomationArtifact stubExtractArtifact(String artifactId) {
        AutomationArtifact artifact = artifact(artifactId, AutomationArtifact.ArtifactStatus.GENERATED, AutomationArtifact.SourceKind.GENERATED_EXTRACT);
        when(artifactService.requireExtractArtifact(artifactId)).thenReturn(artifact);
        when(artifactService.summary(artifact)).thenReturn(artifactSummary(artifactId));
        return artifact;
    }

    private AutomationArtifact artifact(String artifactId,
                                        AutomationArtifact.ArtifactStatus status,
                                        AutomationArtifact.SourceKind sourceKind) {
        AutomationArtifact artifact = new AutomationArtifact();
        artifact.setId(artifactId);
        artifact.setTenantId("tenant-1");
        artifact.setWorkspaceId("workspace-1");
        artifact.setSourceKind(sourceKind);
        artifact.setStatus(status);
        artifact.setObjectKey("tenants/tenant-1/workspaces/workspace-1/automation-artifacts/" + artifactId + "/import.csv");
        artifact.setContentType("text/csv");
        artifact.setSizeBytes(128L);
        artifact.setSha256("a".repeat(64));
        artifact.setRetentionPolicy("AUTOMATION_30_DAYS");
        return artifact;
    }

    private Map<String, Object> artifactSummary(String artifactId) {
        return Map.of(
                "artifactId", artifactId,
                "sourceKind", "UPLOAD",
                "status", "READY",
                "contentType", "text/csv",
                "sizeBytes", 128L,
                "sha256", "a".repeat(64),
                "retentionPolicy", "AUTOMATION_30_DAYS");
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
    void createActivityPersistsDependencyContract() {
        AutomationActivity dependency = new AutomationActivity();
        dependency.setId("activity-dep");
        dependency.setTenantId("tenant-1");
        dependency.setWorkspaceId("workspace-1");
        dependency.setName("Upstream SQL");
        dependency.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        dependency.setInputConfig("{\"sql\":\"SELECT email FROM subscribers\"}");
        dependency.setOutputConfig("{}");

        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Dependent SQL"))
                .thenReturn(false);
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-dep", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(dependency));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> {
            AutomationActivity activity = invocation.getArgument(0);
            activity.setId("activity-1");
            return activity;
        });

        AutomationStudioDto.ActivityResponse response = service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Dependent SQL")
                .activityType(AutomationStudioDto.ActivityType.SQL_QUERY)
                .dependencyActivityIds(List.of("activity-dep"))
                .failurePolicy(AutomationStudioDto.FailurePolicy.SKIP_DEPENDENTS)
                .inputConfig(Map.of("sql", "SELECT email FROM subscribers"))
                .outputConfig(Map.of())
                .build());

        assertThat(response.getDependencyActivityIds()).containsExactly("activity-dep");
        assertThat(response.getFailurePolicy()).isEqualTo(AutomationStudioDto.FailurePolicy.SKIP_DEPENDENTS);
    }

    @Test
    void createActivityRejectsDependencyOutsideCurrentWorkspace() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Dependent SQL"))
                .thenReturn(false);
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("foreign-activity", "tenant-1", "workspace-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Dependent SQL")
                .activityType(AutomationStudioDto.ActivityType.SQL_QUERY)
                .dependencyActivityIds(List.of("foreign-activity"))
                .inputConfig(Map.of("sql", "SELECT email FROM subscribers"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("Dependency activity must exist in the current workspace");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
    }

    @Test
    void updateActivityRejectsDependencyCycle() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Daily SQL");
        activity.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        activity.setInputConfig("{\"sql\":\"SELECT email FROM customers\"}");
        activity.setOutputConfig("{}");

        AutomationActivity dependency = new AutomationActivity();
        dependency.setId("activity-2");
        dependency.setTenantId("tenant-1");
        dependency.setWorkspaceId("workspace-1");
        dependency.setName("Upstream SQL");
        dependency.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        dependency.setDependencyActivityIds("[\"activity-1\"]");

        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-2", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(dependency));
        when(activityRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc("tenant-1", "workspace-1"))
                .thenReturn(List.of(activity, dependency));

        assertThatThrownBy(() -> service.updateActivity("activity-1", AutomationStudioDto.ActivityRequest.builder()
                .name("Daily SQL")
                .activityType(AutomationStudioDto.ActivityType.SQL_QUERY)
                .dependencyActivityIds(List.of("activity-2"))
                .inputConfig(Map.of("sql", "SELECT email FROM customers"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("dependencies must not contain cycles");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
    }

    @Test
    void createUnsupportedActivityWithValidShapeIsNotVerifiedAsExecutable() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Script"))
                .thenReturn(false);
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> {
            AutomationActivity activity = invocation.getArgument(0);
            activity.setId("activity-1");
            return activity;
        });

        AutomationStudioDto.ActivityResponse response = service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Script")
                .activityType(AutomationStudioDto.ActivityType.SCRIPT)
                .inputConfig(Map.of("scriptRef", "script-artifact-1"))
                .outputConfig(Map.of())
                .build());

        assertThat(response.getVerification()).containsEntry("valid", false);
        assertThat(response.getVerification().get("errors").toString()).contains("SCRIPT activity execution is not supported");
        assertThat(response.getVerification().get("normalizedConfig").toString()).contains("liveExecutionSupported=false");
    }

    @Test
    void createUnsupportedActiveActivityFailsBeforePersistence() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Script"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Script")
                .activityType(AutomationStudioDto.ActivityType.SCRIPT)
                .status(AutomationStudioDto.ActivityStatus.ACTIVE)
                .inputConfig(Map.of("scriptRef", "script-artifact-1"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("ACTIVE automation activities require verification.valid=true and liveExecutionSupported=true")
                .hasMessageContaining("SCRIPT activity execution is not supported");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
    }

    @Test
    void createFileActivityRejectsRawObjectKeyBeforePersistence() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Subscriber Import"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Subscriber Import")
                .activityType(AutomationStudioDto.ActivityType.IMPORT)
                .inputConfig(Map.of(
                        "sourceLocation", "https://storage.example.com/import.csv",
                        "targetType", "SUBSCRIBER",
                        "fieldMapping", Map.of("email", "Email Address")))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("scoped artifactId");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
    }

    @Test
    void createActivityRejectsSensitiveNestedConfigBeforePersistence() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Sensitive SQL"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Sensitive SQL")
                .activityType(AutomationStudioDto.ActivityType.SQL_QUERY)
                .inputConfig(Map.of(
                        "sql", "SELECT email FROM subscribers",
                        "connection", Map.of("apiKey", "raw-value")))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("inputConfig.connection.apiKey");

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
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Script"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.updateActivity("activity-1", AutomationStudioDto.ActivityRequest.builder()
                .name("Script")
                .activityType(AutomationStudioDto.ActivityType.SCRIPT)
                .status(AutomationStudioDto.ActivityStatus.ACTIVE)
                .inputConfig(Map.of("scriptRef", "script-artifact-1"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("ACTIVE automation activities require verification.valid=true and liveExecutionSupported=true")
                .hasMessageContaining("SCRIPT activity execution is not supported");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
    }

    @Test
    void createWebhookActivityRejectsRawEndpointBeforePersistence() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Webhook"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Webhook")
                .activityType(AutomationStudioDto.ActivityType.WEBHOOK)
                .inputConfig(Map.of("url", "https://example.com/hook", "method", "POST"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("raw endpoint");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
    }

    @Test
    void createWebhookActivityVerifiesPlatformEventContract() {
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
                .inputConfig(Map.of(
                        "eventToDispatch", "automation.activity.completed",
                        "webhookAuthRef", "whref-1",
                        "data", Map.of("status", "completed")))
                .outputConfig(Map.of())
                .build());

        assertThat(response.getVerification()).containsEntry("valid", true);
        assertThat(response.getVerification().get("normalizedConfig").toString())
                .contains("liveExecutionSupported=true")
                .contains("automation.activity.completed")
                .doesNotContain("url");
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
    void sqlActivityRunRecordsTraceAndDependencyMetadata() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Daily SQL");
        activity.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setDependencyActivityIds("[\"activity-upstream\"]");
        activity.setFailurePolicy(AutomationStudioDto.FailurePolicy.SKIP_DEPENDENTS);
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

        assertThat(response.getTraceId()).isNotBlank();
        assertThat(response.getDependencyTrace()).containsEntry("dependencyCount", 1);
        assertThat(response.getDependencyTrace()).containsEntry("failurePolicy", "SKIP_DEPENDENTS");
        assertThat(response.getDependencyTrace().get("dependencyActivityIds")).isEqualTo(List.of("activity-upstream"));
        assertThat(response.getResult()).containsKey("dependencyTrace");
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
        assertThat(response.getErrorMessage()).contains("Activity execution failed");
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
        activity.setInputConfig("{\"artifactId\":\"artifact-1\",\"targetType\":\"SUBSCRIBER\",\"fieldMapping\":{\"email\":\"Email Address\"}}");
        activity.setOutputConfig("{}");
        stubImportArtifact("artifact-1");
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
        activity.setInputConfig("{\"artifactId\":\"artifact-1\",\"targetType\":\"SUBSCRIBER\",\"fieldMapping\":{\"email\":\"Email Address\"}}");
        activity.setOutputConfig("{}");
        stubImportArtifact("artifact-1");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).triggerSource("TEST").build()))
                .hasMessageContaining("confirmLiveRun=true");

        verify(runRepository, never()).save(any(AutomationActivityRun.class));
        verify(audienceDataExtensionClient, never()).startImportActivity(any(), any(), any());
    }

    @Test
    void liveRunRequiresIdempotencyKeyBeforeSideEffects() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Subscriber Import");
        activity.setActivityType(AutomationStudioDto.ActivityType.IMPORT);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"artifactId\":\"artifact-1\",\"targetType\":\"SUBSCRIBER\",\"fieldMapping\":{\"email\":\"Email Address\"}}");
        activity.setOutputConfig("{}");
        stubImportArtifact("artifact-1");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).triggerSource("TEST").build()))
                .hasMessageContaining("idempotencyKey");

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
        activity.setInputConfig("{\"artifactId\":\"artifact-1\",\"targetType\":\"SUBSCRIBER\",\"fieldMapping\":{\"email\":\"Email Address\"}}");
        activity.setOutputConfig("{}");
        AutomationArtifact artifact = stubImportArtifact("artifact-1");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("import-run-1")))
                .thenReturn(true);
        when(audienceDataExtensionClient.startImportActivity(eq("tenant-1"), eq("workspace-1"), any()))
                .thenReturn(Map.of("id", "import-1", "status", "PENDING", "targetType", "SUBSCRIBER"));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).idempotencyKey("import-run-1").triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("importJobId", "import-1");
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(audienceDataExtensionClient).startImportActivity(eq("tenant-1"), eq("workspace-1"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).containsEntry("fileName", artifact.getObjectKey());
        assertThat(response.getResult()).doesNotContainKey("importJob");
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
        activity.setInputConfig("{\"artifactId\":\"artifact-1\",\"targetType\":\"DATA_EXTENSION\",\"targetId\":\"de-1\",\"fieldMapping\":{\"email\":\"Email\",\"score\":\"Score\"}}");
        activity.setOutputConfig("{}");
        stubImportArtifact("artifact-1");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("import-run-2")))
                .thenReturn(true);
        when(audienceDataExtensionClient.startImportActivity(eq("tenant-1"), eq("workspace-1"), any()))
                .thenReturn(Map.of("id", "import-2", "status", "PENDING", "targetType", "DATA_EXTENSION"));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).idempotencyKey("import-run-2").triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("importJobId", "import-2");
        verify(audienceDataExtensionClient).startImportActivity(eq("tenant-1"), eq("workspace-1"), any());
    }

    @Test
    void duplicateLiveImportRunSkipsAudienceSideEffect() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Subscriber Import");
        activity.setActivityType(AutomationStudioDto.ActivityType.IMPORT);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"artifactId\":\"artifact-1\",\"targetType\":\"SUBSCRIBER\",\"fieldMapping\":{\"email\":\"Email Address\"}}");
        activity.setOutputConfig("{}");
        stubImportArtifact("artifact-1");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("import-run-1")))
                .thenReturn(false);
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).idempotencyKey("import-run-1").triggerSource("FILE_DROP").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("duplicateSkipped", true);
        verify(audienceDataExtensionClient, never()).startImportActivity(any(), any(), any());
    }

    @Test
    void webhookActivityLiveRunPublishesGuardedPlatformEvent() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Webhook");
        activity.setActivityType(AutomationStudioDto.ActivityType.WEBHOOK);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"eventToDispatch\":\"automation.activity.completed\",\"webhookAuthRef\":\"whref-1\",\"data\":{\"status\":\"completed\"}}");
        activity.setOutputConfig("{}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("webhook-run-1")))
                .thenReturn(true);
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).idempotencyKey("webhook-run-1").triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("publishedTopic", AppConstants.TOPIC_WEBHOOK_TRIGGERED);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(workflowEventPublisher).publishAction(eq(AppConstants.TOPIC_WEBHOOK_TRIGGERED), eq("tenant-1"), any(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("eventToDispatch", "automation.activity.completed")
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("ownershipScope", "WORKSPACE")
                .containsEntry("idempotencyKey", "webhook-run-1");
        assertThat(payloadCaptor.getValue().get("data").toString()).contains("status=completed").doesNotContain("whref-1");
    }

    @Test
    void duplicateLiveWebhookRunSkipsPlatformPublish() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Webhook");
        activity.setActivityType(AutomationStudioDto.ActivityType.WEBHOOK);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"eventToDispatch\":\"automation.activity.completed\"}");
        activity.setOutputConfig("{}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("webhook-run-1")))
                .thenReturn(false);
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).idempotencyKey("webhook-run-1").triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("duplicateSkipped", true);
        verify(workflowEventPublisher, never()).publishAction(any(), any(), any(), any());
    }

    @Test
    void notificationActivityLiveRunPublishesTerminalPlatformEvent() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Failure notification");
        activity.setActivityType(AutomationStudioDto.ActivityType.NOTIFICATION);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"userId\":\"user-1\",\"title\":\"Automation failed\",\"message\":\"Run failed\",\"severity\":\"ERROR\",\"terminalStatus\":\"FAILED\",\"linkUrl\":\"/app/automation\"}");
        activity.setOutputConfig("{}");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("notification-run-1")))
                .thenReturn(true);
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(false).confirmLiveRun(true).idempotencyKey("notification-run-1").triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("publishedTopic", AppConstants.TOPIC_NOTIFICATION_CREATED);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(workflowEventPublisher).publishAction(eq(AppConstants.TOPIC_NOTIFICATION_CREATED), eq("tenant-1"), any(), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("userId", "user-1")
                .containsEntry("severity", "ERROR")
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("idempotencyKey", "notification-run-1");
    }

    @Test
    void createNotificationActivityRejectsNonTerminalStatus() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Notify"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Notify")
                .activityType(AutomationStudioDto.ActivityType.NOTIFICATION)
                .status(AutomationStudioDto.ActivityStatus.ACTIVE)
                .inputConfig(Map.of(
                        "userId", "user-1",
                        "title", "Running",
                        "message", "Run is in progress",
                        "terminalStatus", "RUNNING"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("terminalStatus must be SUCCEEDED or FAILED");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
    }

    @Test
    void fileDropDryRunRecordsScopedArtifactMetadataOnly() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("File Drop");
        activity.setActivityType(AutomationStudioDto.ActivityType.FILE_DROP);
        activity.setStatus(AutomationStudioDto.ActivityStatus.DRAFT);
        activity.setInputConfig("{\"artifactId\":\"artifact-1\"}");
        activity.setOutputConfig("{}");
        stubImportArtifact("artifact-1");
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

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.VERIFIED);
        assertThat(response.getResult()).containsKey("artifact");
        assertThat(response.getResult().toString()).doesNotContain("objectKey");
        verify(audienceDataExtensionClient, never()).startImportActivity(any(), any(), any());
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
        activity.setInputConfig("{\"sourceType\":\"DATA_EXTENSION\",\"sourceId\":\"de-1\"}");
        activity.setOutputConfig("{\"artifactId\":\"artifact-extract\"}");
        stubExtractArtifact("artifact-extract");
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

    @Test
    void listRunsUsesBoundedLimitAndTenantWorkspaceScope() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Daily SQL");
        activity.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));

        AutomationActivityRun run = new AutomationActivityRun();
        run.setId("run-1");
        run.setTenantId("tenant-1");
        run.setWorkspaceId("workspace-1");
        run.setActivityId("activity-1");
        run.setTraceId("trace-1");
        run.setDependencyTraceJson("{\"dependencyCount\":0}");
        when(runRepository.findByTenantIdAndWorkspaceIdAndActivityIdOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), eq("activity-1"), any(Pageable.class)))
                .thenReturn(List.of(run));

        List<AutomationStudioDto.RunResponse> runs = service.listRuns("activity-1", 1000);

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(runRepository).findByTenantIdAndWorkspaceIdAndActivityIdOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), eq("activity-1"), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(200);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getTraceId()).isEqualTo("trace-1");
    }
}
