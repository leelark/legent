package com.legent.automation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.automation.client.AudienceDataExtensionClient;
import com.legent.automation.domain.AutomationActivity;
import com.legent.automation.domain.AutomationActivityRun;
import com.legent.automation.dto.AutomationStudioDto;
import com.legent.automation.repository.AutomationActivityRepository;
import com.legent.automation.repository.AutomationActivityRunRepository;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutomationStudioService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AutomationActivityRepository activityRepository;
    private final AutomationActivityRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final AudienceDataExtensionClient audienceDataExtensionClient;

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
        AutomationStudioDto.VerificationResponse verification = verifyConfig(activity.getActivityType(), readMap(activity.getInputConfig()), readMap(activity.getOutputConfig()));
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
        AutomationStudioDto.VerificationResponse verification = verifyConfig(activity.getActivityType(), readMap(activity.getInputConfig()), readMap(activity.getOutputConfig()));
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
        if (!dryRun && activity.getStatus() != AutomationStudioDto.ActivityStatus.ACTIVE) {
            throw new ValidationException("status", "Only ACTIVE activities can run outside dry-run mode");
        }

        AutomationActivityRun run = new AutomationActivityRun();
        run.setTenantId(activity.getTenantId());
        run.setWorkspaceId(activity.getWorkspaceId());
        run.setActivityId(activity.getId());
        run.setDryRun(dryRun);
        run.setTriggerSource(safeRequest.getTriggerSource() == null ? "MANUAL" : safeRequest.getTriggerSource());
        run.setStartedAt(Instant.now());

        if (!verification.isValid()) {
            run.setStatus(AutomationStudioDto.RunStatus.FAILED);
            run.setErrorMessage(String.join("; ", verification.getErrors()));
            run.setResultJson(writeJson(Map.of("verification", verification)));
        } else {
            try {
                Map<String, Object> result = runResult(activity, safeRequest);
                run.setRowsRead(asLong(result.get("rowsRead")));
                run.setRowsWritten(asLong(result.get("rowsWritten")));
                run.setStatus(dryRun ? AutomationStudioDto.RunStatus.VERIFIED : AutomationStudioDto.RunStatus.SUCCEEDED);
                run.setResultJson(writeJson(result));
            } catch (RuntimeException ex) {
                run.setStatus(AutomationStudioDto.RunStatus.FAILED);
                String errorMessage = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                run.setErrorMessage(errorMessage);
                run.setRowsRead(0L);
                run.setRowsWritten(0L);
                run.setResultJson(writeJson(Map.of(
                        "activityType", activity.getActivityType().name(),
                        "dryRun", dryRun,
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
        requireActivity(activityId);
        return runRepository.findByTenantIdAndWorkspaceIdAndActivityIdOrderByCreatedAtDesc(requireTenant(), requireWorkspace(), activityId)
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
        activity.setInputConfig(writeJson(request.getInputConfig() == null ? Map.of() : request.getInputConfig()));
        activity.setOutputConfig(writeJson(request.getOutputConfig() == null ? Map.of() : request.getOutputConfig()));
    }

    private AutomationStudioDto.VerificationResponse verifyConfig(AutomationStudioDto.ActivityType type,
                                                                  Map<String, Object> input,
                                                                  Map<String, Object> output) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("activityType", type);
        normalized.put("inputConfig", input);
        normalized.put("outputConfig", output);
        if (type == null) {
            errors.add("activityType is required");
        } else {
            switch (type) {
                case SQL_QUERY -> verifySql(input, output, errors, warnings);
                case FILE_DROP -> require(input, "locationPattern", errors);
                case IMPORT -> verifyImport(input, errors);
                case EXTRACT -> {
                    require(input, "sourceType", errors);
                    require(output, "destination", errors);
                }
                case SCRIPT -> verifyScript(input, errors, warnings);
                case WEBHOOK -> verifyWebhook(input, errors);
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

    private void verifyImport(Map<String, Object> input, List<String> errors) {
        String sourceLocation = asString(input.get("sourceLocation"));
        if (sourceLocation == null) {
            errors.add("Import activity requires inputConfig.sourceLocation");
        } else {
            String normalized = sourceLocation.toLowerCase(Locale.ROOT);
            if (sourceLocation.length() > 255 || sourceLocation.contains("..") || sourceLocation.contains("\\")
                    || sourceLocation.startsWith("/") || !normalized.endsWith(".csv")) {
                errors.add("Import activity sourceLocation must be a safe CSV object key");
            }
        }

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

    private void verifyWebhook(Map<String, Object> input, List<String> errors) {
        String url = asString(input.get("url"));
        if (url == null || !url.startsWith("https://")) {
            errors.add("Webhook activity requires https inputConfig.url");
        }
        String method = asString(input.get("method"));
        if (method != null && !List.of("GET", "POST", "PUT", "PATCH").contains(method.toUpperCase(Locale.ROOT))) {
            errors.add("Webhook method must be GET, POST, PUT, or PATCH");
        }
    }

    private Map<String, Object> runResult(AutomationActivity activity, AutomationStudioDto.RunRequest request) {
        if (activity.getActivityType() == AutomationStudioDto.ActivityType.SQL_QUERY) {
            return runSqlQueryActivity(activity, request);
        }
        if (activity.getActivityType() == AutomationStudioDto.ActivityType.IMPORT) {
            return runImportActivity(activity, request);
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

    private Map<String, Object> runImportActivity(AutomationActivity activity, AutomationStudioDto.RunRequest request) {
        Map<String, Object> input = readMap(activity.getInputConfig());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileName", input.get("sourceLocation"));
        body.put("fileSize", input.get("fileSize"));
        body.put("targetType", input.getOrDefault("targetType", "SUBSCRIBER"));
        body.put("targetId", input.get("targetId"));
        body.put("fieldMapping", asStringMap(input.get("fieldMapping")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activityType", activity.getActivityType().name());
        result.put("dryRun", request.isDryRun());
        result.put("rowsRead", 0L);
        result.put("rowsWritten", 0L);
        result.put("checkedAt", Instant.now().toString());
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
        result.put("importJob", importResponse);
        result.put("importJobId", importResponse.get("id"));
        result.put("importStatus", importResponse.get("status"));
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
                .inputConfig(readMap(activity.getInputConfig()))
                .outputConfig(readMap(activity.getOutputConfig()))
                .verification(readMap(activity.getVerificationJson()))
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
                .errorMessage(run.getErrorMessage())
                .result(readMap(run.getResultJson()))
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

    private String requireTenant() {
        return TenantContext.requireTenantId();
    }

    private String requireWorkspace() {
        return TenantContext.requireWorkspaceId();
    }
}
