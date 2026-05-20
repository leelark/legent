package com.legent.automation.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.common.util.IdGenerator;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutomationStudioService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final int DEFAULT_RUN_LIMIT = 50;
    private static final int MAX_RUN_LIMIT = 200;
    private static final String ACTIVITY_RUN_EVENT_TYPE = "automation.activity.run";
    private static final Set<AutomationStudioDto.ActivityType> LIVE_EXECUTION_SUPPORTED_TYPES = EnumSet.of(
            AutomationStudioDto.ActivityType.SQL_QUERY,
            AutomationStudioDto.ActivityType.IMPORT,
            AutomationStudioDto.ActivityType.WEBHOOK,
            AutomationStudioDto.ActivityType.NOTIFICATION);
    private static final Set<AutomationStudioDto.ActivityType> VALIDATION_ONLY_ACTIVITY_TYPES = EnumSet.of(
            AutomationStudioDto.ActivityType.FILE_DROP,
            AutomationStudioDto.ActivityType.EXTRACT);
    private static final int MAX_WEBHOOK_DATA_KEYS = 20;
    private static final int MAX_WEBHOOK_DATA_VALUE_CHARS = 512;
    private static final int MAX_NOTIFICATION_TITLE_CHARS = 120;
    private static final int MAX_NOTIFICATION_MESSAGE_CHARS = 500;
    private static final Set<String> TERMINAL_NOTIFICATION_STATUSES = Set.of("SUCCEEDED", "FAILED");
    private static final Set<String> NOTIFICATION_SEVERITIES = Set.of("INFO", "WARNING", "ERROR", "SUCCESS");
    private static final Set<String> SENSITIVE_KEY_NAMES = Set.of(
            "password",
            "secret",
            "token",
            "apikey",
            "accesskey",
            "privatekey",
            "authorization",
            "cookie",
            "smtppassword");
    private static final Set<String> ALLOWED_REFERENCE_KEYS = Set.of(
            "credentialref",
            "webhookauthref",
            "storageconnectionref",
            "scriptartifactref");

    private final AutomationActivityRepository activityRepository;
    private final AutomationActivityRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final AudienceDataExtensionClient audienceDataExtensionClient;
    private final AutomationArtifactService artifactService;
    private final AutomationEventIdempotencyService idempotencyService;
    private final WorkflowEventPublisher workflowEventPublisher;

    @Transactional(readOnly = true)
    public List<AutomationStudioDto.ActivityResponse> listActivities() {
        return activityRepository
                .findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(requireTenant(), requireWorkspace())
                .stream()
                .map(this::toActivityResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AutomationStudioDto.ActivityResponse getActivity(String id) {
        return toActivityResponse(requireActivity(id));
    }

    @Transactional
    public AutomationStudioDto.ActivityResponse createActivity(AutomationStudioDto.ActivityRequest request) {
        String tenantId = requireTenant();
        String workspaceId = requireWorkspace();
        if (activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(tenantId, workspaceId, request.getName())) {
            throw new ConflictException("AutomationActivity", "name", request.getName());
        }
        AutomationActivity activity = new AutomationActivity();
        activity.setTenantId(tenantId);
        activity.setWorkspaceId(workspaceId);
        apply(activity, request);
        validateDependencyContract(activity);
        AutomationStudioDto.VerificationResponse verification = verifyConfig(activity.getActivityType(), readMap(activity.getInputConfig()), readMap(activity.getOutputConfig()));
        requireVerifiedBeforeActive(activity, verification);
        activity.setVerificationJson(writeJson(verification));
        return toActivityResponse(activityRepository.save(activity));
    }

    @Transactional
    public AutomationStudioDto.ActivityResponse updateActivity(String id, AutomationStudioDto.ActivityRequest request) {
        AutomationActivity activity = requireActivity(id);
        if (request.getName() != null && !request.getName().equals(activity.getName())
                && activityRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(requireTenant(), requireWorkspace(), request.getName())) {
            throw new ConflictException("AutomationActivity", "name", request.getName());
        }
        apply(activity, request);
        validateDependencyContract(activity);
        AutomationStudioDto.VerificationResponse verification = verifyConfig(activity.getActivityType(), readMap(activity.getInputConfig()), readMap(activity.getOutputConfig()));
        requireVerifiedBeforeActive(activity, verification);
        activity.setVerificationJson(writeJson(verification));
        return toActivityResponse(activityRepository.save(activity));
    }

    @Transactional
    public void archiveActivity(String id) {
        AutomationActivity activity = requireActivity(id);
        activity.setStatus(AutomationStudioDto.ActivityStatus.ARCHIVED);
        activity.softDelete();
        activityRepository.save(activity);
    }

    @Transactional
    public AutomationStudioDto.VerificationResponse verifyActivity(String id) {
        AutomationActivity activity = requireActivity(id);
        AutomationStudioDto.VerificationResponse response = verifyConfig(activity.getActivityType(), readMap(activity.getInputConfig()), readMap(activity.getOutputConfig()));
        activity.setVerificationJson(writeJson(response));
        activityRepository.save(activity);
        return response;
    }

    @Transactional
    public AutomationStudioDto.RunResponse runActivity(String id, AutomationStudioDto.RunRequest request) {
        AutomationActivity activity = requireActivity(id);
        AutomationStudioDto.RunRequest safeRequest = request == null ? AutomationStudioDto.RunRequest.builder().dryRun(true).build() : request;
        AutomationStudioDto.VerificationResponse verification = verifyConfig(activity.getActivityType(), readMap(activity.getInputConfig()), readMap(activity.getOutputConfig()));
        boolean dryRun = safeRequest.isDryRun();
        if (!dryRun) {
            requireLiveRunIntent(activity, safeRequest);
        }
        String idempotencyKey = normalizeBlank(safeRequest.getIdempotencyKey());

        AutomationActivityRun run = new AutomationActivityRun();
        run.setId(IdGenerator.newId());
        run.setTenantId(activity.getTenantId());
        run.setWorkspaceId(activity.getWorkspaceId());
        run.setActivityId(activity.getId());
        run.setDryRun(dryRun);
        run.setTriggerSource(safeRequest.getTriggerSource() == null ? "MANUAL" : safeRequest.getTriggerSource());
        run.setStartedAt(Instant.now());
        run.setTraceId(UUID.randomUUID().toString());
        run.setIdempotencyKey(idempotencyKey);
        Map<String, Object> dependencyTrace = dependencyTrace(activity);
        run.setDependencyTraceJson(writeJson(dependencyTrace));

        if (!verification.isValid()) {
            run.setStatus(AutomationStudioDto.RunStatus.FAILED);
            run.setErrorCode("VERIFICATION_FAILED");
            run.setErrorMessage(String.join("; ", verification.getErrors()));
            run.setResultJson(writeJson(Map.of(
                    "verification", verification,
                    "traceId", run.getTraceId(),
                    "dependencyTrace", dependencyTrace)));
        } else {
            boolean idempotencyClaimed = false;
            try {
                if (!dryRun && requiresSideEffectIdempotency(activity)) {
                    if (!idempotencyService.claimIfNew(
                            activity.getTenantId(),
                            activity.getWorkspaceId(),
                            ACTIVITY_RUN_EVENT_TYPE,
                            null,
                            idempotencyKey)) {
                        run.setStatus(AutomationStudioDto.RunStatus.SUCCEEDED);
                        run.setRowsRead(0L);
                        run.setRowsWritten(0L);
                        run.setResultJson(writeJson(Map.of(
                                "activityType", activity.getActivityType().name(),
                                "dryRun", false,
                                "duplicateSkipped", true,
                                "traceId", run.getTraceId(),
                                "idempotencyKey", idempotencyKey,
                                "dependencyTrace", dependencyTrace,
                                "message", "Duplicate live automation run skipped.")));
                        run.setCompletedAt(Instant.now());
                        AutomationActivityRun savedRun = runRepository.save(run);
                        activity.setLastRunAt(savedRun.getCompletedAt());
                        activityRepository.save(activity);
                        return toRunResponse(savedRun);
                    }
                    idempotencyClaimed = true;
                }
                Map<String, Object> result = sanitizeForPersistence(runResult(activity, safeRequest, run));
                result.put("traceId", run.getTraceId());
                result.put("dependencyTrace", dependencyTrace);
                if (idempotencyKey != null) {
                    result.put("idempotencyKey", idempotencyKey);
                }
                run.setRowsRead(asLong(result.get("rowsRead")));
                run.setRowsWritten(asLong(result.get("rowsWritten")));
                run.setStatus(dryRun ? AutomationStudioDto.RunStatus.VERIFIED : AutomationStudioDto.RunStatus.SUCCEEDED);
                run.setResultJson(writeJson(result));
                if (idempotencyClaimed) {
                    idempotencyService.markProcessed(
                            activity.getTenantId(),
                            activity.getWorkspaceId(),
                            ACTIVITY_RUN_EVENT_TYPE,
                            null,
                            idempotencyKey);
                }
            } catch (RuntimeException ex) {
                if (idempotencyClaimed) {
                    idempotencyService.releaseClaim(
                            activity.getTenantId(),
                            activity.getWorkspaceId(),
                            ACTIVITY_RUN_EVENT_TYPE,
                            null,
                            idempotencyKey);
                }
                run.setStatus(AutomationStudioDto.RunStatus.FAILED);
                run.setErrorCode(errorCode(ex));
                String errorMessage = safeErrorMessage(ex);
                run.setErrorMessage(errorMessage);
                run.setRowsRead(0L);
                run.setRowsWritten(0L);
                run.setResultJson(writeJson(Map.of(
                        "activityType", activity.getActivityType().name(),
                        "dryRun", dryRun,
                        "traceId", run.getTraceId(),
                        "dependencyTrace", dependencyTrace,
                        "error", errorMessage)));
            }
        }
        run.setCompletedAt(Instant.now());
        AutomationActivityRun savedRun = runRepository.save(run);
        activity.setLastRunAt(savedRun.getCompletedAt());
        activityRepository.save(activity);
        return toRunResponse(savedRun);
    }

    @Transactional(readOnly = true)
    public List<AutomationStudioDto.RunResponse> listRuns(String activityId) {
        return listRuns(activityId, DEFAULT_RUN_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<AutomationStudioDto.RunResponse> listRuns(String activityId, int limit) {
        requireActivity(activityId);
        Pageable page = PageRequest.of(0, boundedRunLimit(limit));
        return runRepository.findByTenantIdAndWorkspaceIdAndActivityIdOrderByCreatedAtDesc(requireTenant(), requireWorkspace(), activityId, page)
                .stream()
                .map(this::toRunResponse)
                .toList();
    }

    private void apply(AutomationActivity activity, AutomationStudioDto.ActivityRequest request) {
        if (request.getName() != null) {
            activity.setName(request.getName().trim());
        }
        if (request.getActivityType() != null) {
            activity.setActivityType(request.getActivityType());
        }
        if (request.getStatus() != null) {
            activity.setStatus(request.getStatus());
        }
        activity.setScheduleExpression(normalizeBlank(request.getScheduleExpression()));
        if (request.getDependencyActivityIds() != null) {
            activity.setDependencyActivityIds(writeJson(normalizeDependencyIds(request.getDependencyActivityIds())));
        }
        if (request.getFailurePolicy() != null) {
            activity.setFailurePolicy(request.getFailurePolicy());
        } else if (activity.getFailurePolicy() == null) {
            activity.setFailurePolicy(AutomationStudioDto.FailurePolicy.STOP_ON_FAILURE);
        }
        Map<String, Object> inputConfig = request.getInputConfig() == null ? Map.of() : request.getInputConfig();
        Map<String, Object> outputConfig = request.getOutputConfig() == null ? Map.of() : request.getOutputConfig();
        rejectSensitiveKeys("inputConfig", inputConfig);
        rejectSensitiveKeys("outputConfig", outputConfig);
        rejectRawFileActivityReferences(request.getActivityType(), inputConfig, outputConfig);
        rejectRawWebhookActivityReferences(request.getActivityType(), inputConfig);
        activity.setInputConfig(writeJson(inputConfig));
        activity.setOutputConfig(writeJson(outputConfig));
    }

    private void validateDependencyContract(AutomationActivity activity) {
        List<String> dependencyIds = readStringList(activity.getDependencyActivityIds());
        if (dependencyIds.isEmpty()) {
            return;
        }
        String activityId = activity.getId();
        for (String dependencyId : dependencyIds) {
            if (activityId != null && activityId.equals(dependencyId)) {
                throw new ValidationException("dependencyActivityIds", "Automation activity cannot depend on itself");
            }
            activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(
                            dependencyId,
                            activity.getTenantId(),
                            activity.getWorkspaceId())
                    .orElseThrow(() -> new ValidationException("dependencyActivityIds",
                            "Dependency activity must exist in the current workspace: " + dependencyId));
        }
        requireNoDependencyCycle(activity, dependencyIds);
    }

    private void requireNoDependencyCycle(AutomationActivity activity, List<String> dependencyIds) {
        if (activity.getId() == null) {
            return;
        }
        Map<String, List<String>> graph = new LinkedHashMap<>();
        activityRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        activity.getTenantId(),
                        activity.getWorkspaceId())
                .forEach(existing -> graph.put(existing.getId(), readStringList(existing.getDependencyActivityIds())));
        graph.put(activity.getId(), dependencyIds);
        if (hasPathToActivity(activity.getId(), dependencyIds, graph, new HashSet<>())) {
            throw new ValidationException("dependencyActivityIds", "Automation activity dependencies must not contain cycles");
        }
    }

    private boolean hasPathToActivity(String targetActivityId,
                                      List<String> dependencyIds,
                                      Map<String, List<String>> graph,
                                      Set<String> visited) {
        for (String dependencyId : dependencyIds) {
            if (targetActivityId.equals(dependencyId)) {
                return true;
            }
            if (visited.add(dependencyId)
                    && hasPathToActivity(targetActivityId, graph.getOrDefault(dependencyId, List.of()), graph, visited)) {
                return true;
            }
        }
        return false;
    }

    private AutomationStudioDto.VerificationResponse verifyConfig(AutomationStudioDto.ActivityType type,
                                                                  Map<String, Object> input,
                                                                  Map<String, Object> output) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("activityType", type);
        normalized.put("inputConfig", sanitizeForPersistence(input));
        normalized.put("outputConfig", sanitizeForPersistence(output));
        collectSensitiveKeyErrors("inputConfig", input, errors);
        collectSensitiveKeyErrors("outputConfig", output, errors);
        if (type == null) {
            errors.add("activityType is required");
        } else {
            boolean liveExecutionSupported = LIVE_EXECUTION_SUPPORTED_TYPES.contains(type);
            boolean validationOnly = VALIDATION_ONLY_ACTIVITY_TYPES.contains(type);
            normalized.put("liveExecutionSupported", liveExecutionSupported);
            normalized.put("validationOnly", validationOnly);
            if (!liveExecutionSupported && !validationOnly) {
                errors.add(type.name() + " activity execution is not supported in Automation Studio. Supported live activity types: SQL_QUERY, IMPORT, WEBHOOK, NOTIFICATION.");
            } else if (validationOnly) {
                warnings.add(type.name() + " activity supports dry-run validation only; live file movement is not enabled.");
            }
            switch (type) {
                case SQL_QUERY -> verifySql(input, output, errors, warnings);
                case FILE_DROP -> verifyFileDrop(input, errors, normalized);
                case IMPORT -> verifyImport(input, errors, normalized);
                case EXTRACT -> verifyExtract(input, output, errors, normalized);
                case SCRIPT -> verifyScript(input, errors, warnings);
                case WEBHOOK -> verifyWebhook(input, errors, normalized);
                case NOTIFICATION -> verifyNotification(input, errors, normalized);
            }
        }
        return AutomationStudioDto.VerificationResponse.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .warnings(warnings)
                .normalizedConfig(normalized)
                .build();
    }

    private void verifySql(Map<String, Object> input, Map<String, Object> output, List<String> errors, List<String> warnings) {
        String sql = asString(input.get("sql"));
        if (sql == null) {
            errors.add("SQL activity requires inputConfig.sql");
            return;
        }
        String normalizedSql = sql.stripLeading().toUpperCase(Locale.ROOT);
        if (!normalizedSql.startsWith("SELECT ")) {
            errors.add("SQL activity allows safe SELECT statements only");
        }
        if (sql.contains(";") || sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            errors.add("SQL activity accepts one comment-free statement");
        }
        List<String> forbidden = List.of("INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ", "TRUNCATE ", "CREATE ");
        for (String token : forbidden) {
            if (normalizedSql.contains(token)) {
                errors.add("SQL activity contains forbidden token: " + token.trim());
            }
        }
        String writeMode = asString(output.get("writeMode"));
        if (writeMode != null && !List.of("APPEND", "OVERWRITE", "UPDATE", "UPSERT").contains(writeMode.toUpperCase(Locale.ROOT))) {
            errors.add("SQL activity outputConfig.writeMode must be APPEND, OVERWRITE, UPDATE, or UPSERT");
        }
        Long maxRows = asLong(input.get("maxRows"));
        if (maxRows != null && (maxRows < 1 || maxRows > 5000)) {
            errors.add("SQL activity inputConfig.maxRows must be between 1 and 5000");
        }
        if (asString(output.get("targetDataExtensionId")) == null) {
            warnings.add("SQL activity has no outputConfig.targetDataExtensionId; result will not populate a data extension.");
        }
    }

    private void verifyFileDrop(Map<String, Object> input, List<String> errors, Map<String, Object> normalized) {
        if (asString(input.get("locationPattern")) != null || asString(input.get("sourceLocation")) != null) {
            errors.add("File drop activities require inputConfig.artifactId; raw locations and object keys are not accepted");
        }
        resolveArtifactSummary(input, "artifactId", true, errors, normalized, "artifact");
    }

    private void verifyImport(Map<String, Object> input, List<String> errors, Map<String, Object> normalized) {
        if (asString(input.get("sourceLocation")) != null) {
            errors.add("Import activity requires inputConfig.artifactId; sourceLocation object keys are not accepted");
        }
        resolveArtifactSummary(input, "artifactId", true, errors, normalized, "artifact");

        String targetType = asString(input.get("targetType"));
        if (targetType == null) {
            errors.add("Import activity requires inputConfig.targetType");
        } else if (!List.of("SUBSCRIBER", "DATA_EXTENSION").contains(targetType.toUpperCase(Locale.ROOT))) {
            errors.add("Import activity targetType must be SUBSCRIBER or DATA_EXTENSION");
        }

        Map<String, String> fieldMapping = asStringMap(input.get("fieldMapping"));
        if (fieldMapping.isEmpty()) {
            errors.add("Import activity requires inputConfig.fieldMapping");
        } else if ("SUBSCRIBER".equalsIgnoreCase(targetType) && asString(fieldMapping.get("email")) == null) {
            errors.add("Import activity requires fieldMapping.email for subscriber imports");
        }
        if ("DATA_EXTENSION".equalsIgnoreCase(targetType) && asString(input.get("targetId")) == null) {
            errors.add("Import activity requires inputConfig.targetId for data extension imports");
        }
    }

    private void verifyExtract(Map<String, Object> input,
                               Map<String, Object> output,
                               List<String> errors,
                               Map<String, Object> normalized) {
        String sourceType = asString(input.get("sourceType"));
        if (sourceType == null) {
            errors.add("Extract activity requires inputConfig.sourceType");
        } else if (!List.of("DATA_EXTENSION", "IMPORT_ARTIFACT", "GENERATED_EXTRACT").contains(sourceType.toUpperCase(Locale.ROOT))) {
            errors.add("Extract activity sourceType must be DATA_EXTENSION, IMPORT_ARTIFACT, or GENERATED_EXTRACT");
        }
        if ("DATA_EXTENSION".equalsIgnoreCase(sourceType) && asString(input.get("sourceId")) == null) {
            errors.add("Extract activity requires inputConfig.sourceId for data extension extracts");
        }
        if ("IMPORT_ARTIFACT".equalsIgnoreCase(sourceType) || "GENERATED_EXTRACT".equalsIgnoreCase(sourceType)) {
            resolveArtifactSummary(input, "sourceArtifactId", true, errors, normalized, "sourceArtifact");
        }
        if (asString(output.get("destination")) != null) {
            errors.add("Extract activity requires outputConfig.artifactId; raw destinations are not accepted");
        }
        resolveArtifactSummary(output, "artifactId", false, errors, normalized, "outputArtifact");
    }

    private void resolveArtifactSummary(Map<String, Object> config,
                                        String key,
                                        boolean importArtifact,
                                        List<String> errors,
                                        Map<String, Object> normalized,
                                        String normalizedKey) {
        String artifactId = asString(config.get(key));
        if (artifactId == null) {
            errors.add("Missing required config: " + key);
            return;
        }
        try {
            AutomationArtifact artifact = importArtifact
                    ? artifactService.requireImportArtifact(artifactId)
                    : artifactService.requireExtractArtifact(artifactId);
            normalized.put(normalizedKey, artifactService.summary(artifact));
        } catch (ValidationException ex) {
            errors.add(ex.getMessage());
        }
    }

    private void verifyScript(Map<String, Object> input, List<String> errors, List<String> warnings) {
        require(input, "scriptRef", errors);
        if (input.containsKey("inlineScript")) {
            errors.add("Inline scripts are not accepted; use signed scriptRef artifacts");
        }
        Long maxRuntime = asLong(input.get("maxRuntimeSeconds"));
        if (maxRuntime != null && maxRuntime > 300) {
            warnings.add("Script maxRuntimeSeconds above 300 needs operations approval.");
        }
    }

    private void verifyWebhook(Map<String, Object> input, List<String> errors, Map<String, Object> normalized) {
        String eventToDispatch = asString(input.get("eventToDispatch"));
        if (eventToDispatch == null) {
            errors.add("Webhook activity requires inputConfig.eventToDispatch");
        } else if (!isAutomationPlatformEvent(eventToDispatch)) {
            errors.add("Webhook eventToDispatch must start with automation. and use letters, numbers, dots, dashes, or underscores");
        } else {
            normalized.put("eventToDispatch", eventToDispatch.trim().toLowerCase(Locale.ROOT));
        }
        String webhookAuthRef = asString(input.get("webhookAuthRef"));
        if (webhookAuthRef != null) {
            if (!isSafeReference(webhookAuthRef)) {
                errors.add("Webhook activity webhookAuthRef must be an opaque reference");
            } else {
                normalized.put("webhookAuthRef", webhookAuthRef.trim());
            }
        }
        Object data = input.get("data");
        if (data != null) {
            try {
                Map<String, Object> boundedData = boundedWebhookData(data);
                normalized.put("dataKeys", boundedData.keySet().stream().toList());
            } catch (ValidationException ex) {
                errors.add(ex.getMessage());
            }
        }
    }

    private void verifyNotification(Map<String, Object> input, List<String> errors, Map<String, Object> normalized) {
        String userId = asString(input.get("userId"));
        if (userId == null) {
            errors.add("Notification activity requires inputConfig.userId");
        } else if (!isSafeReference(userId)) {
            errors.add("Notification userId must be an opaque user reference");
        }
        String title = asString(input.get("title"));
        if (title == null) {
            errors.add("Notification activity requires inputConfig.title");
        } else if (title.length() > MAX_NOTIFICATION_TITLE_CHARS) {
            errors.add("Notification title is too long");
        }
        String message = asString(input.get("message"));
        if (message == null) {
            errors.add("Notification activity requires inputConfig.message");
        } else if (message.length() > MAX_NOTIFICATION_MESSAGE_CHARS) {
            errors.add("Notification message is too long");
        }
        String terminalStatus = asString(input.get("terminalStatus"));
        if (terminalStatus == null) {
            errors.add("Notification activity requires terminal inputConfig.terminalStatus");
        } else if (!TERMINAL_NOTIFICATION_STATUSES.contains(terminalStatus.toUpperCase(Locale.ROOT))) {
            errors.add("Notification terminalStatus must be SUCCEEDED or FAILED");
        } else {
            normalized.put("terminalStatus", terminalStatus.toUpperCase(Locale.ROOT));
        }
        String severity = asString(input.get("severity"));
        if (severity != null && !NOTIFICATION_SEVERITIES.contains(severity.toUpperCase(Locale.ROOT))) {
            errors.add("Notification severity must be INFO, WARNING, ERROR, or SUCCESS");
        }
        String linkUrl = asString(input.get("linkUrl"));
        if (linkUrl != null && !isSafeRelativeLink(linkUrl)) {
            errors.add("Notification linkUrl must be an application-relative path");
        }
    }

    private Map<String, Object> runResult(AutomationActivity activity, AutomationStudioDto.RunRequest request, AutomationActivityRun run) {
        if (activity.getActivityType() == AutomationStudioDto.ActivityType.SQL_QUERY) {
            return runSqlQueryActivity(activity, request);
        }
        if (activity.getActivityType() == AutomationStudioDto.ActivityType.IMPORT) {
            return runImportActivity(activity, request);
        }
        if (activity.getActivityType() == AutomationStudioDto.ActivityType.FILE_DROP) {
            return runFileDropActivity(activity, request);
        }
        if (activity.getActivityType() == AutomationStudioDto.ActivityType.EXTRACT) {
            return runExtractActivity(activity, request);
        }
        if (activity.getActivityType() == AutomationStudioDto.ActivityType.WEBHOOK) {
            return runWebhookActivity(activity, request, run);
        }
        if (activity.getActivityType() == AutomationStudioDto.ActivityType.NOTIFICATION) {
            return runNotificationActivity(activity, request, run);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (!request.isDryRun()) {
            throw new UnsupportedOperationException(activity.getActivityType().name() + " activity execution is not implemented");
        }
        result.put("activityType", activity.getActivityType().name());
        result.put("dryRun", request.isDryRun());
        result.put("rowsRead", asLong(valueFromOverrides(request, "rowsRead")) == null ? 0L : asLong(valueFromOverrides(request, "rowsRead")));
        result.put("rowsWritten", request.isDryRun() ? 0L : (asLong(valueFromOverrides(request, "rowsWritten")) == null ? 0L : asLong(valueFromOverrides(request, "rowsWritten"))));
        result.put("checkedAt", Instant.now().toString());
        result.put("message", request.isDryRun() ? "Verification completed; no side effects applied." : "Activity run recorded for orchestration history.");
        return result;
    }

    private Map<String, Object> runFileDropActivity(AutomationActivity activity, AutomationStudioDto.RunRequest request) {
        if (!request.isDryRun()) {
            throw new UnsupportedOperationException("FILE_DROP activity execution is not supported");
        }
        AutomationArtifact artifact = artifactService.requireImportArtifact(asString(readMap(activity.getInputConfig()).get("artifactId")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityType", activity.getActivityType().name());
        result.put("dryRun", true);
        result.put("rowsRead", 0L);
        result.put("rowsWritten", 0L);
        result.put("checkedAt", Instant.now().toString());
        result.put("artifact", artifactService.summary(artifact));
        result.put("message", "File drop artifact validation completed; no file movement applied.");
        return result;
    }

    private Map<String, Object> runExtractActivity(AutomationActivity activity, AutomationStudioDto.RunRequest request) {
        if (!request.isDryRun()) {
            throw new UnsupportedOperationException("EXTRACT activity execution is not supported");
        }
        Map<String, Object> input = readMap(activity.getInputConfig());
        Map<String, Object> output = readMap(activity.getOutputConfig());
        AutomationArtifact outputArtifact = artifactService.requireExtractArtifact(asString(output.get("artifactId")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityType", activity.getActivityType().name());
        result.put("dryRun", true);
        result.put("rowsRead", 0L);
        result.put("rowsWritten", 0L);
        result.put("checkedAt", Instant.now().toString());
        result.put("sourceType", asString(input.get("sourceType")));
        result.put("outputArtifact", artifactService.summary(outputArtifact));
        result.put("message", "Extract artifact validation completed; no file movement applied.");
        return result;
    }

    private Map<String, Object> runImportActivity(AutomationActivity activity, AutomationStudioDto.RunRequest request) {
        Map<String, Object> input = readMap(activity.getInputConfig());
        AutomationArtifact artifact = artifactService.requireImportArtifact(asString(input.get("artifactId")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileName", artifact.getObjectKey());
        body.put("fileSize", artifact.getSizeBytes());
        body.put("targetType", input.getOrDefault("targetType", "SUBSCRIBER"));
        body.put("targetId", input.get("targetId"));
        body.put("fieldMapping", asStringMap(input.get("fieldMapping")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityType", activity.getActivityType().name());
        result.put("dryRun", request.isDryRun());
        result.put("rowsRead", 0L);
        result.put("rowsWritten", 0L);
        result.put("checkedAt", Instant.now().toString());
        result.put("artifact", artifactService.summary(artifact));
        if (request.isDryRun()) {
            result.put("message", "Import activity verified; no import job started.");
            result.put("targetType", body.get("targetType"));
            result.put("fieldMappingSize", ((Map<?, ?>) body.get("fieldMapping")).size());
            return result;
        }

        Map<String, Object> importResponse = new LinkedHashMap<>(audienceDataExtensionClient.startImportActivity(
                activity.getTenantId(),
                activity.getWorkspaceId(),
                body));
        result.put("message", "Audience import job started.");
        result.put("importJobId", importResponse.get("id"));
        result.put("importStatus", importResponse.get("status"));
        return result;
    }

    private Map<String, Object> runWebhookActivity(AutomationActivity activity,
                                                   AutomationStudioDto.RunRequest request,
                                                   AutomationActivityRun run) {
        Map<String, Object> input = readMap(activity.getInputConfig());
        String eventToDispatch = asString(input.get("eventToDispatch"));
        Map<String, Object> data = boundedWebhookData(input.get("data"));
        data.put("activityId", activity.getId());
        data.put("activityName", activity.getName());
        data.put("activityRunId", run.getId());
        data.put("traceId", run.getTraceId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityType", activity.getActivityType().name());
        result.put("dryRun", request.isDryRun());
        result.put("rowsRead", 0L);
        result.put("rowsWritten", 0L);
        result.put("checkedAt", Instant.now().toString());
        result.put("eventToDispatch", eventToDispatch);
        result.put("dataKeys", data.keySet().stream().sorted().toList());
        result.put("deliveryBoundary", "platform.webhook");
        if (request.isDryRun()) {
            result.put("message", "Webhook activity verified; no platform event published.");
            return result;
        }

        Map<String, Object> payload = platformEventPayload(activity, run, request);
        payload.put("eventToDispatch", eventToDispatch);
        payload.put("data", data);
        publishPlatformEvent(AppConstants.TOPIC_WEBHOOK_TRIGGERED,
                activity.getId() + ":" + run.getId(),
                payload,
                request.getIdempotencyKey());
        result.put("message", "Webhook platform event accepted.");
        result.put("publishedTopic", AppConstants.TOPIC_WEBHOOK_TRIGGERED);
        return result;
    }

    private Map<String, Object> runNotificationActivity(AutomationActivity activity,
                                                        AutomationStudioDto.RunRequest request,
                                                        AutomationActivityRun run) {
        Map<String, Object> input = readMap(activity.getInputConfig());
        String terminalStatus = asString(input.get("terminalStatus"));
        if (terminalStatus == null) {
            throw new ValidationException("inputConfig.terminalStatus", "Notification terminalStatus is required");
        }
        terminalStatus = terminalStatus.toUpperCase(Locale.ROOT);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityType", activity.getActivityType().name());
        result.put("dryRun", request.isDryRun());
        result.put("rowsRead", 0L);
        result.put("rowsWritten", 0L);
        result.put("checkedAt", Instant.now().toString());
        result.put("terminalStatus", terminalStatus);
        result.put("deliveryBoundary", "platform.notification");
        if (request.isDryRun()) {
            result.put("message", "Notification activity verified; no platform event published.");
            return result;
        }

        Map<String, Object> payload = platformEventPayload(activity, run, request);
        payload.put("userId", asString(input.get("userId")));
        payload.put("title", boundedString(asString(input.get("title")), MAX_NOTIFICATION_TITLE_CHARS));
        payload.put("message", boundedString(asString(input.get("message")), MAX_NOTIFICATION_MESSAGE_CHARS));
        payload.put("severity", asString(input.get("severity")) == null
                ? ("FAILED".equals(terminalStatus) ? "ERROR" : "INFO")
                : asString(input.get("severity")).toUpperCase(Locale.ROOT));
        String linkUrl = asString(input.get("linkUrl"));
        if (linkUrl != null) {
            payload.put("linkUrl", linkUrl);
        }

        publishPlatformEvent(AppConstants.TOPIC_NOTIFICATION_CREATED,
                activity.getId() + ":" + run.getId(),
                payload,
                request.getIdempotencyKey());
        result.put("message", "Notification platform event accepted.");
        result.put("publishedTopic", AppConstants.TOPIC_NOTIFICATION_CREATED);
        result.put("recipientUserId", asString(input.get("userId")));
        return result;
    }

    private Map<String, Object> runSqlQueryActivity(AutomationActivity activity, AutomationStudioDto.RunRequest request) {
        Map<String, Object> input = readMap(activity.getInputConfig());
        Map<String, Object> output = readMap(activity.getOutputConfig());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sql", input.get("sql"));
        body.put("targetDataExtensionId", output.get("targetDataExtensionId"));
        body.put("writeMode", output.getOrDefault("writeMode", "APPEND"));
        body.put("maxRows", firstNonNull(
                valueFromOverrides(request, "maxRows"),
                input.get("maxRows"),
                output.get("maxRows")));
        body.put("dryRun", request.isDryRun());

        Map<String, Object> result = new LinkedHashMap<>(audienceDataExtensionClient.runSqlQueryActivity(
                activity.getTenantId(),
                activity.getWorkspaceId(),
                body));
        result.put("activityType", activity.getActivityType().name());
        result.put("checkedAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> dependencyTrace(AutomationActivity activity) {
        List<String> dependencyIds = readStringList(activity.getDependencyActivityIds());
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("dependencyActivityIds", dependencyIds);
        trace.put("dependencyCount", dependencyIds.size());
        trace.put("failurePolicy", activity.getFailurePolicy() == null
                ? AutomationStudioDto.FailurePolicy.STOP_ON_FAILURE.name()
                : activity.getFailurePolicy().name());
        trace.put("status", dependencyIds.isEmpty() ? "NONE" : "VALIDATION_ONLY");
        return trace;
    }

    private String errorCode(RuntimeException ex) {
        if (ex instanceof UnsupportedOperationException) {
            return "UNSUPPORTED_ACTIVITY_EXECUTION";
        }
        if (ex instanceof ValidationException) {
            return "VALIDATION_FAILED";
        }
        return "ACTIVITY_EXECUTION_FAILED";
    }

    private String safeErrorMessage(RuntimeException ex) {
        if (ex instanceof UnsupportedOperationException || ex instanceof ValidationException) {
            return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        return "Activity execution failed; see run trace.";
    }

    private Object valueFromOverrides(AutomationStudioDto.RunRequest request, String key) {
        return request.getOverrides() == null ? null : request.getOverrides().get(key);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private void require(Map<String, Object> map, String key, List<String> errors) {
        if (asString(map.get(key)) == null && !(map.get(key) instanceof Map<?, ?>)) {
            errors.add("Missing required config: " + key);
        }
    }

    private void rejectSensitiveKeys(String path, Object value) {
        List<String> errors = new ArrayList<>();
        collectSensitiveKeyErrors(path, value, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(path, errors.get(0));
        }
    }

    private void rejectRawFileActivityReferences(AutomationStudioDto.ActivityType type,
                                                 Map<String, Object> input,
                                                 Map<String, Object> output) {
        if (type == null || !(type == AutomationStudioDto.ActivityType.FILE_DROP
                || type == AutomationStudioDto.ActivityType.IMPORT
                || type == AutomationStudioDto.ActivityType.EXTRACT)) {
            return;
        }
        if (asString(input.get("sourceLocation")) != null || asString(input.get("locationPattern")) != null) {
            throw new ValidationException("inputConfig", "File activity configs must use scoped artifactId references");
        }
        if (asString(output.get("destination")) != null) {
            throw new ValidationException("outputConfig", "Extract activity output must use a scoped artifactId reference");
        }
        rejectUnsafeArtifactId("inputConfig.artifactId", input.get("artifactId"));
        rejectUnsafeArtifactId("inputConfig.sourceArtifactId", input.get("sourceArtifactId"));
        rejectUnsafeArtifactId("outputConfig.artifactId", output.get("artifactId"));
    }

    private void rejectRawWebhookActivityReferences(AutomationStudioDto.ActivityType type, Map<String, Object> input) {
        if (type != AutomationStudioDto.ActivityType.WEBHOOK) {
            return;
        }
        for (String rawKey : List.of("url", "endpoint", "headers", "method", "body", "authorizationHeader", "cookieHeader")) {
            if (input.containsKey(rawKey)) {
                throw new ValidationException("inputConfig." + rawKey,
                        "Webhook activities use platform webhook subscriptions; raw endpoint, method, header, and body config is not accepted");
            }
        }
    }

    private void rejectUnsafeArtifactId(String path, Object value) {
        String artifactId = asString(value);
        if (artifactId == null) {
            return;
        }
        String lower = artifactId.toLowerCase(Locale.ROOT);
        if (artifactId.contains("..")
                || artifactId.contains("/")
                || artifactId.contains("\\")
                || artifactId.startsWith(".")
                || artifactId.startsWith("/")
                || lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("s3://")
                || lower.startsWith("gs://")
                || lower.startsWith("file:")) {
            throw new ValidationException(path, "File activity artifact references must be opaque scoped artifact IDs");
        }
    }

    private void collectSensitiveKeyErrors(String path, Object value, List<String> errors) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = asString(entry.getKey());
                if (key == null) {
                    continue;
                }
                String nextPath = path + "." + key;
                if (isSensitiveKey(key)) {
                    errors.add(nextPath + " must use a scoped reference field, not raw sensitive material");
                }
                collectSensitiveKeyErrors(nextPath, entry.getValue(), errors);
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                collectSensitiveKeyErrors(path + "[" + i + "]", list.get(i), errors);
            }
        }
    }

    private boolean isAutomationPlatformEvent(String value) {
        String normalized = normalizeBlank(value);
        return normalized != null
                && normalized.length() <= 128
                && normalized.toLowerCase(Locale.ROOT).startsWith("automation.")
                && normalized.matches("[A-Za-z0-9._-]+");
    }

    private boolean isSafeReference(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null || normalized.length() > 128) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return !normalized.contains("..")
                && !normalized.contains("/")
                && !normalized.contains("\\")
                && !normalized.startsWith(".")
                && !lower.startsWith("http://")
                && !lower.startsWith("https://")
                && !lower.startsWith("file:")
                && normalized.matches("[A-Za-z0-9._:-]+");
    }

    private boolean isSafeRelativeLink(String value) {
        String normalized = normalizeBlank(value);
        return normalized != null
                && normalized.startsWith("/")
                && !normalized.startsWith("//")
                && !normalized.contains("\\")
                && !normalized.toLowerCase(Locale.ROOT).startsWith("/api/")
                && normalized.length() <= 256;
    }

    private Map<String, Object> boundedWebhookData(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new ValidationException("inputConfig.data", "Webhook data must be a JSON object");
        }
        if (map.size() > MAX_WEBHOOK_DATA_KEYS) {
            throw new ValidationException("inputConfig.data", "Webhook data has too many keys");
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = asString(entry.getKey());
            if (key == null || !key.matches("[A-Za-z0-9._-]+") || key.length() > 64) {
                throw new ValidationException("inputConfig.data", "Webhook data keys must be compact identifiers");
            }
            if (isSensitiveKey(key)) {
                throw new ValidationException("inputConfig.data." + key,
                        "Webhook data must not contain raw sensitive material");
            }
            result.put(key, boundedWebhookDataValue(entry.getValue(), "inputConfig.data." + key));
        }
        return result;
    }

    private Object boundedWebhookDataValue(Object value, String path) {
        if (value == null || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof CharSequence text) {
            return boundedString(text.toString(), MAX_WEBHOOK_DATA_VALUE_CHARS);
        }
        if (value instanceof List<?> list) {
            if (list.size() > 20) {
                throw new ValidationException(path, "Webhook data arrays are too large");
            }
            return list.stream().map(item -> boundedWebhookDataValue(item, path)).toList();
        }
        throw new ValidationException(path, "Webhook data values must be strings, numbers, booleans, or arrays");
    }

    private String boundedString(String value, int maxChars) {
        String normalized = asString(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() > maxChars ? normalized.substring(0, maxChars) : normalized;
    }

    private Map<String, Object> platformEventPayload(AutomationActivity activity,
                                                     AutomationActivityRun run,
                                                     AutomationStudioDto.RunRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", activity.getTenantId());
        payload.put("workspaceId", activity.getWorkspaceId());
        payload.put("ownershipScope", "WORKSPACE");
        payload.put("activityId", activity.getId());
        payload.put("activityRunId", run.getId());
        payload.put("activityType", activity.getActivityType().name());
        payload.put("traceId", run.getTraceId());
        payload.put("idempotencyKey", normalizeBlank(request.getIdempotencyKey()));
        return payload;
    }

    private void publishPlatformEvent(String topic,
                                      String partitionKey,
                                      Map<String, Object> payload,
                                      String idempotencyKey) {
        String previousRequestId = TenantContext.getRequestId();
        TenantContext.setRequestId(idempotencyKey);
        try {
            workflowEventPublisher.publishAction(topic, requireTenant(), partitionKey, payload);
        } finally {
            TenantContext.setRequestId(previousRequestId);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeForPersistence(Map<String, Object> value) {
        return (Map<String, Object>) sanitizeValue(value);
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = asString(entry.getKey());
                if (key == null) {
                    continue;
                }
                sanitized.put(key, isSensitiveKey(key) ? "[REDACTED]" : sanitizeValue(entry.getValue()));
            }
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeValue).toList();
        }
        if (value instanceof String stringValue && stringValue.length() > 2048) {
            return stringValue.substring(0, 2048);
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = normalizeKey(key);
        if (ALLOWED_REFERENCE_KEYS.contains(normalized)) {
            return false;
        }
        return SENSITIVE_KEY_NAMES.stream().anyMatch(normalized::contains);
    }

    private String normalizeKey(String key) {
        return key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private void requireVerifiedBeforeActive(AutomationActivity activity, AutomationStudioDto.VerificationResponse verification) {
        Map<String, Object> normalizedConfig = verification.getNormalizedConfig() == null
                ? Map.of()
                : verification.getNormalizedConfig();
        if (activity.getStatus() == AutomationStudioDto.ActivityStatus.ACTIVE
                && (!verification.isValid() || !Boolean.TRUE.equals(normalizedConfig.get("liveExecutionSupported")))) {
            throw new ValidationException("status", "ACTIVE automation activities require verification.valid=true and liveExecutionSupported=true: "
                    + String.join("; ", verification.getErrors()));
        }
    }

    private void requireLiveRunIntent(AutomationActivity activity, AutomationStudioDto.RunRequest request) {
        if (!request.isLiveRunConfirmed()) {
            throw new ValidationException("confirmLiveRun", "Live automation runs require confirmLiveRun=true");
        }
        if (activity.getStatus() != AutomationStudioDto.ActivityStatus.ACTIVE) {
            throw new ValidationException("status", "Only ACTIVE activities can run outside dry-run mode");
        }
        if (requiresSideEffectIdempotency(activity) && normalizeBlank(request.getIdempotencyKey()) == null) {
            throw new ValidationException("idempotencyKey", "Live automation runs require idempotencyKey");
        }
    }

    private boolean requiresSideEffectIdempotency(AutomationActivity activity) {
        return LIVE_EXECUTION_SUPPORTED_TYPES.contains(activity.getActivityType());
    }

    private AutomationActivity requireActivity(String id) {
        return activityRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(id, requireTenant(), requireWorkspace())
                .orElseThrow(() -> new NotFoundException("AutomationActivity", id));
    }

    private AutomationStudioDto.ActivityResponse toActivityResponse(AutomationActivity activity) {
        return AutomationStudioDto.ActivityResponse.builder()
                .id(activity.getId())
                .name(activity.getName())
                .activityType(activity.getActivityType())
                .status(activity.getStatus())
                .scheduleExpression(activity.getScheduleExpression())
                .dependencyActivityIds(readStringList(activity.getDependencyActivityIds()))
                .failurePolicy(activity.getFailurePolicy())
                .inputConfig(sanitizeForPersistence(readMap(activity.getInputConfig())))
                .outputConfig(sanitizeForPersistence(readMap(activity.getOutputConfig())))
                .verification(sanitizeForPersistence(readMap(activity.getVerificationJson())))
                .lastRunAt(activity.getLastRunAt())
                .nextRunAt(activity.getNextRunAt())
                .createdAt(activity.getCreatedAt())
                .updatedAt(activity.getUpdatedAt())
                .build();
    }

    private AutomationStudioDto.RunResponse toRunResponse(AutomationActivityRun run) {
        return AutomationStudioDto.RunResponse.builder()
                .id(run.getId())
                .activityId(run.getActivityId())
                .status(run.getStatus())
                .dryRun(run.isDryRun())
                .triggerSource(run.getTriggerSource())
                .rowsRead(run.getRowsRead())
                .rowsWritten(run.getRowsWritten())
                .traceId(run.getTraceId())
                .errorCode(run.getErrorCode())
                .errorMessage(run.getErrorMessage())
                .idempotencyKey(run.getIdempotencyKey())
                .dependencyTrace(sanitizeForPersistence(readMap(run.getDependencyTraceJson())))
                .result(sanitizeForPersistence(readMap(run.getResultJson())))
                .startedAt(run.getStartedAt())
                .completedAt(run.getCompletedAt())
                .createdAt(run.getCreatedAt())
                .build();
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return normalizeDependencyIds(objectMapper.readValue(json, STRING_LIST_TYPE));
        } catch (ValidationException ex) {
            throw ex;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> normalizeDependencyIds(List<String> rawDependencyIds) {
        if (rawDependencyIds == null || rawDependencyIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String rawDependencyId : rawDependencyIds) {
            String dependencyId = normalizeBlank(rawDependencyId);
            if (dependencyId == null) {
                throw new ValidationException("dependencyActivityIds", "Dependency activity IDs must not be blank");
            }
            if (!seen.add(dependencyId)) {
                throw new ValidationException("dependencyActivityIds", "Dependency activity IDs must not contain duplicates");
            }
            normalized.add(dependencyId);
        }
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new ValidationException("json", "Unable to serialize automation activity config");
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, String> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, mapValue) -> {
            String normalizedKey = asString(key);
            String normalizedValue = asString(mapValue);
            if (normalizedKey != null && normalizedValue != null) {
                result.put(normalizedKey, normalizedValue);
            }
        });
        return result;
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int boundedRunLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return DEFAULT_RUN_LIMIT;
        }
        return Math.min(requestedLimit, MAX_RUN_LIMIT);
    }

    private String requireTenant() {
        return TenantContext.requireTenantId();
    }

    private String requireWorkspace() {
        return TenantContext.requireWorkspaceId();
    }
}
