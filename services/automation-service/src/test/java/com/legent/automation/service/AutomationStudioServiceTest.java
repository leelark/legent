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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationStudioServiceTest {

    @Mock private AutomationActivityRepository activityRepository;
    @Mock private AutomationActivityRunRepository runRepository;
    @Mock private AudienceDataExtensionClient audienceDataExtensionClient;
    @Mock private AutomationArtifactService artifactService;
    @Mock private AutomationObjectStorageAdapter objectStorageAdapter;
    @Mock private AutomationEventIdempotencyService idempotencyService;
    @Mock private AutomationActivityLockService activityLockService;
    @Mock private WorkflowEventPublisher workflowEventPublisher;

    private AutomationStudioService service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        service = new AutomationStudioService(activityRepository, runRepository, new ObjectMapper(), audienceDataExtensionClient,
                artifactService, objectStorageAdapter, idempotencyService, activityLockService, workflowEventPublisher);
        lenient().when(activityLockService.acquire(any(), any(), any(), any(), any(Boolean.class), any(), any()))
                .thenAnswer(invocation -> AutomationActivityLockService.LockLease.acquired(
                        "lock-1",
                        invocation.getArgument(3, String.class),
                        java.time.Instant.now().plusSeconds(900),
                        invocation.getArgument(4, Boolean.class),
                        invocation.getArgument(5, String.class)));
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

    private AutomationArtifact stubMovementSourceArtifact(String artifactId) {
        AutomationArtifact artifact = artifact(artifactId, AutomationArtifact.ArtifactStatus.READY, AutomationArtifact.SourceKind.UPLOAD);
        lenient().when(artifactService.requireImportArtifact(artifactId)).thenReturn(artifact);
        lenient().when(artifactService.requireExtractArtifact(artifactId)).thenReturn(artifact);
        when(artifactService.requireMovementSourceArtifact(artifactId)).thenReturn(artifact);
        when(artifactService.summary(artifact)).thenReturn(artifactSummary(artifactId));
        return artifact;
    }

    private AutomationArtifact stubMovementTargetArtifact(String artifactId) {
        AutomationArtifact artifact = artifact(artifactId, AutomationArtifact.ArtifactStatus.READY, AutomationArtifact.SourceKind.GENERATED_EXTRACT);
        when(artifactService.requireExtractArtifact(artifactId)).thenReturn(artifact);
        lenient().when(artifactService.requireMovementTargetArtifact(artifactId)).thenReturn(artifact);
        lenient().when(artifactService.markGenerated(artifact)).thenAnswer(invocation -> {
            AutomationArtifact target = invocation.getArgument(0);
            target.setStatus(AutomationArtifact.ArtifactStatus.GENERATED);
            return target;
        });
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
    void listActivitiesUsesDefaultFirstPage() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Daily SQL");
        activity.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        activity.setInputConfig("{\"sql\":\"SELECT email FROM subscribers\"}");
        activity.setOutputConfig("{}");
        when(activityRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), any(Pageable.class)))
                .thenReturn(List.of(activity));

        List<AutomationStudioDto.ActivityResponse> activities = service.listActivities();

        assertThat(activities).hasSize(1);
        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(activityRepository).findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(50);
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
    void updateActivityRejectsIndirectDependencyCycleWithoutWorkspaceListScan() {
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
        dependency.setDependencyActivityIds("[\"activity-3\"]");

        AutomationActivity transitiveDependency = new AutomationActivity();
        transitiveDependency.setId("activity-3");
        transitiveDependency.setTenantId("tenant-1");
        transitiveDependency.setWorkspaceId("workspace-1");
        transitiveDependency.setName("Transitive SQL");
        transitiveDependency.setActivityType(AutomationStudioDto.ActivityType.SQL_QUERY);
        transitiveDependency.setDependencyActivityIds("[\"activity-1\"]");

        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-2", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(dependency));
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-3", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(transitiveDependency));

        assertThatThrownBy(() -> service.updateActivity("activity-1", AutomationStudioDto.ActivityRequest.builder()
                .name("Daily SQL")
                .activityType(AutomationStudioDto.ActivityType.SQL_QUERY)
                .dependencyActivityIds(List.of("activity-2"))
                .inputConfig(Map.of("sql", "SELECT email FROM customers"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("dependencies must not contain cycles");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
        verify(activityRepository, never()).findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq("tenant-1"), eq("workspace-1"), any(Pageable.class));
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
    void createSendEmailActivityVerifiesGovernedCampaignHandoff() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Campaign Send"))
                .thenReturn(false);
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> {
            AutomationActivity activity = invocation.getArgument(0);
            activity.setId("activity-1");
            return activity;
        });

        AutomationStudioDto.ActivityResponse response = service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Campaign Send")
                .activityType(AutomationStudioDto.ActivityType.SEND_EMAIL)
                .status(AutomationStudioDto.ActivityStatus.ACTIVE)
                .inputConfig(Map.of(
                        "campaignId", "campaign-1",
                        "scheduledAt", "2026-05-21T00:00:00Z"))
                .outputConfig(Map.of())
                .build());

        assertThat(response.getActivityType()).isEqualTo(AutomationStudioDto.ActivityType.SEND_EMAIL);
        assertThat(response.getVerification()).containsEntry("valid", true);
        assertThat(response.getVerification().get("normalizedConfig").toString())
                .contains("liveExecutionSupported=true")
                .contains("handoffBoundary=CAMPAIGN_ORCHESTRATION")
                .contains("requiresCampaignPreflight=true")
                .contains("requiresSideEffectIdempotency=true");
    }

    @Test
    void createSendEmailActivityRejectsUnsafeOverridesBeforePersistence() {
        when(activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull("tenant-1", "workspace-1", "Unsafe Send"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createActivity(AutomationStudioDto.ActivityRequest.builder()
                .name("Unsafe Send")
                .activityType(AutomationStudioDto.ActivityType.SEND_EMAIL)
                .status(AutomationStudioDto.ActivityStatus.ACTIVE)
                .inputConfig(Map.of(
                        "campaignId", "campaign-1",
                        "recipientEmail", "user@example.com",
                        "skip_suppression", true,
                        "sender-email", "sender@example.com"))
                .outputConfig(Map.of())
                .build()))
                .hasMessageContaining("existing governed campaign")
                .hasMessageContaining("safety controls");

        verify(activityRepository, never()).save(any(AutomationActivity.class));
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
    void liveRunReturnsLockedBeforeIdempotencyOrSideEffects() {
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
        when(activityLockService.acquire(eq("tenant-1"), eq("workspace-1"), eq("activity-1"), any(), eq(false), eq(null), any()))
                .thenReturn(AutomationActivityLockService.LockLease.locked(
                        "lock-older",
                        "run-older",
                        java.time.Instant.now().plusSeconds(120),
                        120L,
                        "Automation activity is already locked by another live run."));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-locked");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder()
                        .dryRun(false)
                        .confirmLiveRun(true)
                        .idempotencyKey("import-run-locked")
                        .triggerSource("TEST")
                        .build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.LOCKED);
        assertThat(response.getRetryAfterSeconds()).isEqualTo(120L);
        assertThat(response.getLockOwnerRunId()).isEqualTo("run-older");
        assertThat(response.getErrorCode()).isEqualTo("ACTIVITY_LOCKED");
        verify(idempotencyService, never()).claimIfNew(any(), any(), any(), any(), any());
        verify(audienceDataExtensionClient, never()).startImportActivity(any(), any(), any());
        verify(activityLockService, never()).release(any(), any(), any(), any());
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
        assertThat(response.getResult()).containsEntry("operatorOverride", false);
        verify(activityLockService).release(eq("tenant-1"), eq("workspace-1"), eq("activity-1"), any());
    }

    @Test
    void liveRunOverridePassesReasonToActivityLock() {
        AutomationActivity activity = sendEmailActivity();
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("send-run-override")))
                .thenReturn(true);
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder()
                        .dryRun(false)
                        .confirmLiveRun(true)
                        .idempotencyKey("send-run-override")
                        .triggerSource("TEST")
                        .operatorOverride(true)
                        .overrideReason("Ops-approved rerun after stuck activity")
                        .build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("operatorOverride", true);
        assertThat(response.getResult()).containsEntry("overrideReason", "Ops-approved rerun after stuck activity");
        verify(activityLockService).acquire(
                eq("tenant-1"),
                eq("workspace-1"),
                eq("activity-1"),
                any(),
                eq(true),
                eq("Ops-approved rerun after stuck activity"),
                any());
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
    void sendEmailActivityDryRunDoesNotPublishCampaignRequest() {
        AutomationActivity activity = sendEmailActivity();
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder().dryRun(true).triggerSource("TEST").build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.VERIFIED);
        assertThat(response.getResult())
                .containsEntry("campaignId", "campaign-1")
                .containsEntry("handoffBoundary", "CAMPAIGN_ORCHESTRATION")
                .containsEntry("requiresCampaignPreflight", true);
        assertThat(response.getResult()).doesNotContainKey("publishedTopic");
        verify(workflowEventPublisher, never()).publishAction(any(), any(), any(), any());
        verify(idempotencyService, never()).claimIfNew(any(), any(), any(), any(), any());
    }

    @Test
    void sendEmailActivityLiveRunPublishesCampaignSendRequested() {
        TenantContext.setEnvironmentId("prod");
        TenantContext.setUserId("user-1");
        AutomationActivity activity = sendEmailActivity();
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("send-run-1")))
                .thenReturn(true);
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder()
                        .dryRun(false)
                        .confirmLiveRun(true)
                        .idempotencyKey("send-run-1")
                        .triggerSource("TEST")
                        .overrides(Map.of("triggerReference", "journey-node-1"))
                        .build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("publishedTopic", AppConstants.TOPIC_SEND_REQUESTED);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(workflowEventPublisher).publishAction(
                eq(AppConstants.TOPIC_SEND_REQUESTED),
                eq("tenant-1"),
                eq("campaign-1:activity-1"),
                payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("tenantId", "tenant-1")
                .containsEntry("workspaceId", "workspace-1")
                .containsEntry("environmentId", "prod")
                .containsEntry("actorId", "user-1")
                .containsEntry("campaignId", "campaign-1")
                .containsEntry("triggerSource", "TEST")
                .containsEntry("triggerReference", "journey-node-1")
                .containsEntry("idempotencyKey", "send-run-1")
                .containsEntry("confirmLaunch", true)
                .containsEntry("activityId", "activity-1")
                .containsEntry("activityType", "SEND_EMAIL")
                .containsEntry("handoffBoundary", "CAMPAIGN_ORCHESTRATION")
                .containsEntry("requiresCampaignPreflight", true)
                .containsEntry("sendLifecycleOwner", "campaign-service");
        assertThat(payloadCaptor.getValue().get("activityRunId")).isNotNull();
        assertThat(payloadCaptor.getValue().get("traceId")).isNotNull();
        verify(idempotencyService).markProcessed("tenant-1", "workspace-1", "automation.activity.run", null, "send-run-1");
    }

    @Test
    void duplicateLiveSendEmailRunSkipsCampaignPublish() {
        AutomationActivity activity = sendEmailActivity();
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("send-run-1")))
                .thenReturn(false);
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder()
                        .dryRun(false)
                        .confirmLiveRun(true)
                        .idempotencyKey("send-run-1")
                        .triggerSource("TEST")
                        .build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("duplicateSkipped", true);
        verify(workflowEventPublisher, never()).publishAction(any(), any(), any(), any());
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
    void fileDropLiveRunMovesScopedArtifactThroughStorageAdapter() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("File Drop");
        activity.setActivityType(AutomationStudioDto.ActivityType.FILE_DROP);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"artifactId\":\"artifact-1\"}");
        activity.setOutputConfig("{\"artifactId\":\"artifact-output\"}");
        AutomationArtifact sourceArtifact = stubImportArtifact("artifact-1");
        AutomationArtifact targetArtifact = stubMovementTargetArtifact("artifact-output");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("file-drop-run-1")))
                .thenReturn(true);
        when(objectStorageAdapter.copyArtifact(eq(sourceArtifact), eq(targetArtifact), any()))
                .thenReturn(new AutomationObjectStorageAdapter.MovementResult(
                        "FILE_DROP_COPY",
                        "artifact-1",
                        "artifact-output",
                        128L,
                        "a".repeat(64),
                        "text/csv",
                        Instant.parse("2026-05-24T08:00:00Z")));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder()
                        .dryRun(false)
                        .confirmLiveRun(true)
                        .idempotencyKey("file-drop-run-1")
                        .triggerSource("TEST")
                        .build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("message", "File drop artifact moved through governed automation storage.");
        assertThat(response.getResult().get("storageMovement").toString())
                .contains("FILE_DROP_COPY")
                .contains("artifact-output")
                .doesNotContain("objectKey")
                .doesNotContain("tenants/");
        verify(objectStorageAdapter).copyArtifact(eq(sourceArtifact), eq(targetArtifact), any());
        verify(idempotencyService).markProcessed("tenant-1", "workspace-1", "automation.activity.run", null, "file-drop-run-1");
        verify(activityLockService).release(eq("tenant-1"), eq("workspace-1"), eq("activity-1"), any());
        verify(audienceDataExtensionClient, never()).startImportActivity(any(), any(), any());
    }

    @Test
    void duplicateLiveFileDropRunSkipsStorageMovement() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("File Drop");
        activity.setActivityType(AutomationStudioDto.ActivityType.FILE_DROP);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"artifactId\":\"artifact-1\"}");
        activity.setOutputConfig("{\"artifactId\":\"artifact-output\"}");
        stubImportArtifact("artifact-1");
        stubMovementTargetArtifact("artifact-output");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("file-drop-run-1")))
                .thenReturn(false);
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder()
                        .dryRun(false)
                        .confirmLiveRun(true)
                        .idempotencyKey("file-drop-run-1")
                        .triggerSource("TEST")
                        .build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("duplicateSkipped", true);
        verify(objectStorageAdapter, never()).copyArtifact(any(), any(), any());
    }

    @Test
    void storageFailureRecordsNormalizedFileMovementError() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("File Drop");
        activity.setActivityType(AutomationStudioDto.ActivityType.FILE_DROP);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"artifactId\":\"artifact-1\"}");
        activity.setOutputConfig("{\"artifactId\":\"artifact-output\"}");
        AutomationArtifact sourceArtifact = stubImportArtifact("artifact-1");
        AutomationArtifact targetArtifact = stubMovementTargetArtifact("artifact-output");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("file-drop-run-1")))
                .thenReturn(true);
        when(objectStorageAdapter.copyArtifact(eq(sourceArtifact), eq(targetArtifact), any()))
                .thenThrow(new AutomationObjectStorageException("Automation file movement failed storage integrity verification"));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder()
                        .dryRun(false)
                        .confirmLiveRun(true)
                        .idempotencyKey("file-drop-run-1")
                        .triggerSource("TEST")
                        .build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo("STORAGE_MOVEMENT_FAILED");
        assertThat(response.getErrorMessage()).contains("storage integrity verification");
        assertThat(response.getResult().toString()).doesNotContain("objectKey").doesNotContain("tenants/");
        verify(idempotencyService).releaseClaim("tenant-1", "workspace-1", "automation.activity.run", null, "file-drop-run-1");
    }

    @Test
    void extractLiveRunMovesArtifactBackedSourceThroughStorageAdapter() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Extract");
        activity.setActivityType(AutomationStudioDto.ActivityType.EXTRACT);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"sourceType\":\"IMPORT_ARTIFACT\",\"sourceArtifactId\":\"artifact-source\"}");
        activity.setOutputConfig("{\"artifactId\":\"artifact-extract\"}");
        AutomationArtifact sourceArtifact = stubMovementSourceArtifact("artifact-source");
        AutomationArtifact outputArtifact = stubMovementTargetArtifact("artifact-extract");
        when(activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull("activity-1", "tenant-1", "workspace-1"))
                .thenReturn(Optional.of(activity));
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("extract-run-1")))
                .thenReturn(true);
        when(objectStorageAdapter.copyArtifact(eq(sourceArtifact), eq(outputArtifact), any()))
                .thenReturn(new AutomationObjectStorageAdapter.MovementResult(
                        "EXTRACT_ARTIFACT_COPY",
                        "artifact-source",
                        "artifact-extract",
                        128L,
                        "a".repeat(64),
                        "text/csv",
                        Instant.parse("2026-05-24T08:00:00Z")));
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder()
                        .dryRun(false)
                        .confirmLiveRun(true)
                        .idempotencyKey("extract-run-1")
                        .triggerSource("TEST")
                        .build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.SUCCEEDED);
        assertThat(response.getResult()).containsEntry("message", "Artifact-backed extract moved through governed automation storage.");
        assertThat(response.getResult().get("storageMovement").toString()).contains("EXTRACT_ARTIFACT_COPY");
        verify(objectStorageAdapter).copyArtifact(eq(sourceArtifact), eq(outputArtifact), any());
    }

    @Test
    void dataExtensionExtractLiveRunRecordsFailedRunWithoutStorageMovement() {
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
        when(idempotencyService.claimIfNew(eq("tenant-1"), eq("workspace-1"), eq("automation.activity.run"), any(), eq("extract-run-unsupported")))
                .thenReturn(true);
        when(runRepository.save(any(AutomationActivityRun.class))).thenAnswer(invocation -> {
            AutomationActivityRun run = invocation.getArgument(0);
            run.setId("run-1");
            return run;
        });
        when(activityRepository.save(any(AutomationActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AutomationStudioDto.RunResponse response = service.runActivity("activity-1",
                AutomationStudioDto.RunRequest.builder()
                        .dryRun(false)
                        .confirmLiveRun(true)
                        .idempotencyKey("extract-run-unsupported")
                        .triggerSource("TEST")
                        .build());

        assertThat(response.getStatus()).isEqualTo(AutomationStudioDto.RunStatus.FAILED);
        assertThat(response.getErrorMessage()).contains("Live DATA_EXTENSION extracts require an extract provider handoff");
        verify(audienceDataExtensionClient, never()).runSqlQueryActivity(any(), any(), any());
        verify(audienceDataExtensionClient, never()).startImportActivity(any(), any(), any());
        verify(objectStorageAdapter, never()).copyArtifact(any(), any(), any());
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

    private AutomationActivity sendEmailActivity() {
        AutomationActivity activity = new AutomationActivity();
        activity.setId("activity-1");
        activity.setTenantId("tenant-1");
        activity.setWorkspaceId("workspace-1");
        activity.setName("Campaign Send");
        activity.setActivityType(AutomationStudioDto.ActivityType.SEND_EMAIL);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ACTIVE);
        activity.setInputConfig("{\"campaignId\":\"campaign-1\",\"scheduledAt\":\"2026-05-21T00:00:00Z\"}");
        activity.setOutputConfig("{}");
        return activity;
    }
}
