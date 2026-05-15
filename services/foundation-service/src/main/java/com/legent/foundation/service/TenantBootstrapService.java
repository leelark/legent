package com.legent.foundation.service;

import com.legent.common.constant.AppConstants;
import com.legent.foundation.domain.TenantBootstrapStatus;
import com.legent.foundation.dto.BootstrapDto;
import com.legent.foundation.dto.ConfigDto;
import com.legent.foundation.dto.CorePlatformDto;
import com.legent.foundation.repository.TenantBootstrapStatusRepository;
import com.legent.kafka.model.EventEnvelope;
import com.legent.kafka.producer.EventPublisher;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBootstrapService {

    private static final String DEFAULT_WORKSPACE_SLUG = "default";
    private static final String DEFAULT_WORKSPACE_NAME = "Default Workspace";
    private static final String DEFAULT_TEAM_NAME = "Default Team";
    private static final String DEFAULT_ENV_KEY = "PRODUCTION";
    private static final String DEFAULT_ENV_NAME = "Production";

    private final TenantBootstrapStatusRepository bootstrapStatusRepository;
    private final CorePlatformService corePlatformService;
    private final ConfigService configService;
    private final EventPublisher eventPublisher;

    @Transactional
    public void requestBootstrap(String tenantId, String organizationName, String organizationSlug, boolean force) {
        TenantBootstrapStatus status = bootstrapStatusRepository.findById(tenantId).orElseGet(TenantBootstrapStatus::new);
        status.setTenantId(tenantId);
        status.setStatus("PENDING");
        status.setMessage(force ? "Bootstrap repair requested" : "Bootstrap requested");
        status.setLastAttemptAt(Instant.now());
        if (force) {
            status.setRetryCount((status.getRetryCount() == null ? 0 : status.getRetryCount()) + 1);
        }
        bootstrapStatusRepository.save(status);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("organizationName", organizationName);
        payload.put("organizationSlug", organizationSlug);
        payload.put("force", force);

        EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                AppConstants.TOPIC_TENANT_BOOTSTRAP_REQUESTED,
                tenantId,
                "foundation-service",
                payload
        );
        publishAndAwait(AppConstants.TOPIC_TENANT_BOOTSTRAP_REQUESTED, envelope);
    }

    @Transactional
    public void bootstrapTenant(String tenantId, String organizationName, String organizationSlug, boolean force) {
        TenantBootstrapStatus status = bootstrapStatusRepository.findById(tenantId).orElseGet(TenantBootstrapStatus::new);
        status.setTenantId(tenantId);
        if (!force && "COMPLETED".equalsIgnoreCase(status.getStatus())) {
            return;
        }

        String prevTenant = TenantContext.getTenantId();
        String prevWorkspace = TenantContext.getWorkspaceId();
        String prevEnvironment = TenantContext.getEnvironmentId();
        String prevUser = TenantContext.getUserId();
        try {
            TenantContext.setTenantId(tenantId);
            TenantContext.setUserId("SYSTEM");

            status.setStatus("IN_PROGRESS");
            status.setMessage("Initializing default platform and module settings");
            status.setLastAttemptAt(Instant.now());
            bootstrapStatusRepository.save(status);

            String orgId = ensureOrganization(tenantId, organizationName, organizationSlug);
            String workspaceId = ensureWorkspace(orgId);
            String teamId = ensureTeam(workspaceId);
            String environmentId = ensureEnvironment(workspaceId);
            ensureGovernanceDefaults(workspaceId);
            ensureModuleDefaults(workspaceId, environmentId);

            status.setWorkspaceId(workspaceId);
            status.setEnvironmentId(environmentId);
            status.setStatus("COMPLETED");
            status.setMessage("Bootstrap completed");
            status.setCompletedAt(Instant.now());
            status.setModules(Map.of(
                    "platformCore", "COMPLETED",
                    "delivery", "COMPLETED",
                    "template", "COMPLETED",
                    "campaign", "COMPLETED",
                    "automation", "COMPLETED",
                    "audience", "COMPLETED",
                    "analytics", "COMPLETED",
                    "team", teamId
            ));
            bootstrapStatusRepository.save(status);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("workspaceId", workspaceId);
            payload.put("environmentId", environmentId);
            payload.put("status", "COMPLETED");
            EventEnvelope<Map<String, Object>> envelope = EventEnvelope.wrap(
                    AppConstants.TOPIC_TENANT_BOOTSTRAP_COMPLETED,
                    tenantId,
                    "foundation-service",
                    payload
            );
            publishAndAwait(AppConstants.TOPIC_TENANT_BOOTSTRAP_COMPLETED, envelope);
        } catch (Exception ex) {
            status.setStatus("FAILED");
            status.setMessage(ex.getMessage());
            status.setRetryCount((status.getRetryCount() == null ? 0 : status.getRetryCount()) + 1);
            bootstrapStatusRepository.save(status);
            throw ex;
        } finally {
            TenantContext.setTenantId(prevTenant);
            TenantContext.setWorkspaceId(prevWorkspace);
            TenantContext.setEnvironmentId(prevEnvironment);
            TenantContext.setUserId(prevUser);
        }
    }

    @Transactional(readOnly = true)
    public BootstrapDto.StatusResponse getStatus(String tenantId) {
        TenantBootstrapStatus status = bootstrapStatusRepository.findById(tenantId).orElseGet(() -> {
            TenantBootstrapStatus empty = new TenantBootstrapStatus();
            empty.setTenantId(tenantId);
            empty.setStatus("PENDING");
            empty.setMessage("Bootstrap not started");
            return empty;
        });

        return BootstrapDto.StatusResponse.builder()
                .tenantId(status.getTenantId())
                .workspaceId(status.getWorkspaceId())
                .environmentId(status.getEnvironmentId())
                .status(status.getStatus())
                .message(status.getMessage())
                .retryCount(status.getRetryCount() == null ? 0 : status.getRetryCount())
                .lastAttemptAt(status.getLastAttemptAt())
                .completedAt(status.getCompletedAt())
                .modules(status.getModules() == null ? Map.of() : status.getModules())
                .build();
    }

    @Transactional
    public BootstrapDto.StatusResponse repair(String tenantId, boolean force) {
        requestBootstrap(tenantId, null, null, force);
        return getStatus(tenantId);
    }

    private String ensureOrganization(String tenantId, String organizationName, String organizationSlug) {
        List<Map<String, Object>> organizations = corePlatformService.listOrganizations();
        if (!organizations.isEmpty()) {
            return String.valueOf(organizations.get(0).get("id"));
        }
        CorePlatformDto.OrganizationRequest request = new CorePlatformDto.OrganizationRequest();
        request.setName(normalize(organizationName) == null ? "Organization " + tenantId : organizationName);
        request.setSlug(normalize(organizationSlug) == null ? ("org-" + tenantId).toLowerCase(Locale.ROOT) : organizationSlug.toLowerCase(Locale.ROOT));
        request.setStatus("ACTIVE");
        request.setMetadata(Map.of("bootstrap", true));
        return String.valueOf(corePlatformService.createOrganization(request).get("id"));
    }

    private String ensureWorkspace(String organizationId) {
        List<Map<String, Object>> workspaces = corePlatformService.listWorkspaces();
        for (Map<String, Object> workspace : workspaces) {
            if (DEFAULT_WORKSPACE_SLUG.equalsIgnoreCase(String.valueOf(workspace.get("slug")))) {
                return String.valueOf(workspace.get("id"));
            }
        }
        CorePlatformDto.WorkspaceRequest request = new CorePlatformDto.WorkspaceRequest();
        request.setOrganizationId(organizationId);
        request.setName(DEFAULT_WORKSPACE_NAME);
        request.setSlug(DEFAULT_WORKSPACE_SLUG);
        request.setStatus("ACTIVE");
        request.setDefaultEnvironment(DEFAULT_ENV_KEY);
        request.setMetadata(Map.of("bootstrap", true));
        return String.valueOf(corePlatformService.createWorkspace(request).get("id"));
    }

    private String ensureTeam(String workspaceId) {
        List<Map<String, Object>> teams = corePlatformService.listTeams();
        for (Map<String, Object> team : teams) {
            if (workspaceId.equals(String.valueOf(team.get("workspace_id")))
                    && DEFAULT_TEAM_NAME.equalsIgnoreCase(String.valueOf(team.get("name")))) {
                return String.valueOf(team.get("id"));
            }
        }
        CorePlatformDto.TeamRequest request = new CorePlatformDto.TeamRequest();
        request.setWorkspaceId(workspaceId);
        request.setName(DEFAULT_TEAM_NAME);
        request.setCode("DEFAULT");
        request.setStatus("ACTIVE");
        request.setMetadata(Map.of("bootstrap", true));
        return String.valueOf(corePlatformService.createTeam(request).get("id"));
    }

    private String ensureEnvironment(String workspaceId) {
        List<Map<String, Object>> environments = corePlatformService.listEnvironments();
        for (Map<String, Object> environment : environments) {
            if (workspaceId.equals(String.valueOf(environment.get("workspace_id")))
                    && DEFAULT_ENV_KEY.equalsIgnoreCase(String.valueOf(environment.get("environment_key")))) {
                return String.valueOf(environment.get("id"));
            }
        }
        CorePlatformDto.EnvironmentRequest request = new CorePlatformDto.EnvironmentRequest();
        request.setWorkspaceId(workspaceId);
        request.setEnvironmentKey(DEFAULT_ENV_KEY);
        request.setDisplayName(DEFAULT_ENV_NAME);
        request.setStatus("ACTIVE");
        request.setMetadata(Map.of("bootstrap", true));
        return String.valueOf(corePlatformService.createEnvironment(request).get("id"));
    }

    private void ensureGovernanceDefaults(String workspaceId) {
        if (corePlatformService.listQuotaPolicies().isEmpty()) {
            upsertQuota(workspaceId, "contacts", 10000L, 25000L);
            upsertQuota(workspaceId, "emails_sent", 100000L, 500000L);
            upsertQuota(workspaceId, "campaigns", 500L, 2500L);
            upsertQuota(workspaceId, "automations", 100L, 500L);
            upsertQuota(workspaceId, "storage_mb", 1024L, 5120L);
        }

        if (corePlatformService.listFeatureControls().isEmpty()) {
            upsertFeature(workspaceId, "core.admin", true, List.of());
            upsertFeature(workspaceId, "delivery.providers", true, List.of("core.admin"));
            upsertFeature(workspaceId, "campaign.approvals", true, List.of("core.admin"));
            upsertFeature(workspaceId, "automation.builder", true, List.of("core.admin"));
            upsertFeature(workspaceId, "analytics.reports", true, List.of("core.admin"));
        }

        if (corePlatformService.listSubscriptions().isEmpty()) {
            CorePlatformDto.SubscriptionRequest request = new CorePlatformDto.SubscriptionRequest();
            request.setPlanKey("STARTER");
            request.setBillingCycle("MONTHLY");
            request.setAutoRenew(true);
            request.setMetadata(Map.of("bootstrap", true));
            corePlatformService.createSubscription(request);
        }
    }

    private void ensureModuleDefaults(String workspaceId, String environmentId) {
        putSetting("admin.layout.preset", "marketing", "system", "SYSTEM", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("admin.feature.pack", "enterprise", "system", "SYSTEM", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);

        putSetting("branding.name", "Legent", "template", "TEMPLATE", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("branding.logo_url", "", "template", "TEMPLATE", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("branding.primary_color", "#0B6E4F", "template", "TEMPLATE", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("branding.secondary_color", "#F4A261", "template", "TEMPLATE", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);

        putSetting("delivery.default_provider", "MAILHOG", "delivery", "DELIVERY", "STRING", ConfigService.SCOPE_TENANT, null, null);
        putSetting("delivery.retry.max_attempts", "5", "delivery", "DELIVERY", "INTEGER", ConfigService.SCOPE_TENANT, null, null);
        putSetting("delivery.warmup.enabled", "true", "delivery", "DELIVERY", "BOOLEAN", ConfigService.SCOPE_TENANT, null, null);

        putSetting("campaign.defaults.sender_name", "Legent Marketing", "campaign", "CAMPAIGN", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("campaign.defaults.sender_email", "no-reply@example.com", "campaign", "CAMPAIGN", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("campaign.defaults.approval_required", "true", "campaign", "CAMPAIGN", "BOOLEAN", ConfigService.SCOPE_WORKSPACE, workspaceId, null);

        putSetting("automation.templates.pack", "enterprise-10", "automation", "AUTOMATION", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("automation.retry.max_attempts", "3", "automation", "AUTOMATION", "INTEGER", ConfigService.SCOPE_WORKSPACE, workspaceId, null);

        putSetting("audience.defaults.tags", "[\"new\",\"lead\"]", "audience", "AUDIENCE", "JSON", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("audience.dedup.mode", "EMAIL_WORKSPACE", "audience", "AUDIENCE", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("audience.import.email_required", "true", "audience", "AUDIENCE", "BOOLEAN", ConfigService.SCOPE_WORKSPACE, workspaceId, null);

        putSetting("template.defaults.pack", "enterprise-10", "template", "TEMPLATE", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("template.preview.dark_mode", "true", "template", "TEMPLATE", "BOOLEAN", ConfigService.SCOPE_WORKSPACE, workspaceId, null);

        putSetting("analytics.attribution.model", "last_touch", "analytics", "ANALYTICS", "STRING", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
        putSetting("analytics.sla.ingest_latency_ms", "60000", "analytics", "ANALYTICS", "INTEGER", ConfigService.SCOPE_ENVIRONMENT, workspaceId, environmentId);
        putSetting("analytics.reports.scheduled_enabled", "true", "analytics", "ANALYTICS", "BOOLEAN", ConfigService.SCOPE_WORKSPACE, workspaceId, null);
    }

    private void upsertQuota(String workspaceId, String metricKey, long softLimit, long hardLimit) {
        CorePlatformDto.QuotaPolicyRequest request = new CorePlatformDto.QuotaPolicyRequest();
        request.setWorkspaceId(workspaceId);
        request.setMetricKey(metricKey);
        request.setSoftLimit(softLimit);
        request.setHardLimit(hardLimit);
        request.setEnabled(true);
        request.setMetadata(Map.of("bootstrap", true));
        corePlatformService.upsertQuotaPolicy(request);
    }

    private void upsertFeature(String workspaceId, String key, boolean enabled, List<String> dependencies) {
        CorePlatformDto.FeatureControlRequest request = new CorePlatformDto.FeatureControlRequest();
        request.setWorkspaceId(workspaceId);
        request.setFeatureKey(key);
        request.setEnabled(enabled);
        request.setSource("TENANT");
        request.setDependencyKeys(dependencies);
        request.setMetadata(Map.of("bootstrap", true));
        corePlatformService.upsertFeatureControl(request);
    }

    private void putSetting(String key,
                            String value,
                            String module,
                            String category,
                            String type,
                            String scope,
                            String workspaceId,
                            String environmentId) {
        ConfigDto.CreateRequest request = ConfigDto.CreateRequest.builder()
                .configKey(key)
                .configValue(value)
                .moduleKey(module)
                .category(category)
                .valueType(type)
                .scopeType(scope)
                .workspaceId(workspaceId)
                .environmentId(environmentId)
                .dependencyKeys("[]")
                .validationSchema("{}")
                .metadata("{\"bootstrap\":true}")
                .description("Bootstrap default")
                .build();
        configService.upsertConfig(TenantContext.getTenantId(), workspaceId, environmentId, request);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private <T> void publishAndAwait(String topic, EventEnvelope<T> envelope) {
        try {
            eventPublisher.publish(topic, envelope).join();
        } catch (CompletionException e) {
            throw publishFailure(topic, e);
        }
    }

    private RuntimeException publishFailure(String topic, CompletionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Failed to publish event to " + topic, cause);
    }

}
