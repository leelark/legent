package com.legent.audience.service;

import com.legent.audience.domain.DataExtension;
import com.legent.audience.domain.DataExtensionField;
import com.legent.audience.domain.DataExtensionRecord;
import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.repository.DataExtensionFieldRepository;
import com.legent.audience.repository.DataExtensionRecordRepository;
import com.legent.audience.repository.DataExtensionRepository;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DataExtensionQueryActivityService {

    private static final int PAGE_SIZE = 500;
    private static final int MAX_ACTIVITY_ROWS = 5_000;
    private static final int MAX_SCAN_ROWS = 50_000;
    private static final Set<String> WRITE_MODES = Set.of("APPEND", "OVERWRITE", "UPDATE", "UPSERT");
    private static final Pattern SELECT_PATTERN = Pattern.compile("(?is)^SELECT\\s+(.+?)\\s+FROM\\s+([^\\s]+)(.*)$");
    private static final Pattern TAIL_PATTERN = Pattern.compile("(?is)^\\s*(?:WHERE\\s+(.+?)(?=\\s+ORDER\\s+BY|\\s+LIMIT|\\s*$))?\\s*(?:ORDER\\s+BY\\s+([A-Za-z][A-Za-z0-9_]*)(?:\\s+(ASC|DESC))?)?\\s*(?:LIMIT\\s+(\\d{1,6}))?\\s*$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,127}$");
    private static final Pattern SOURCE_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,127}$");
    private static final Pattern NULL_CONDITION_PATTERN = Pattern.compile("(?is)^([A-Za-z][A-Za-z0-9_]*)\\s+IS\\s+(NOT\\s+)?NULL$");
    private static final Pattern VALUE_CONDITION_PATTERN = Pattern.compile("(?is)^([A-Za-z][A-Za-z0-9_]*)\\s*(>=|<=|<>|!=|=|>|<|LIKE)\\s*(.+)$");

    private final DataExtensionRepository deRepository;
    private final DataExtensionFieldRepository fieldRepository;
    private final DataExtensionRecordRepository recordRepository;

    @Transactional
    public DataExtensionDto.SqlQueryActivityResponse execute(DataExtensionDto.SqlQueryActivityRequest request) {
        if (request == null || blank(request.getSql())) {
            throw new ValidationException("sql", "SQL activity requires a SELECT statement");
        }

        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        boolean dryRun = !Boolean.FALSE.equals(request.getDryRun());
        String writeMode = normalizeWriteMode(request.getWriteMode());
        int maxRows = clampMaxRows(request.getMaxRows());
        SqlQuery query = parseSql(request.getSql());
        int effectiveLimit = Math.min(query.limit() == null ? maxRows : query.limit(), maxRows);

        DataExtension source = requireSource(tenantId, workspaceId, query.source());
        DataExtension target = null;
        if (!blank(request.getTargetDataExtensionId())) {
            target = requireDataExtension(tenantId, workspaceId, request.getTargetDataExtensionId());
        } else if (!dryRun) {
            throw new ValidationException("targetDataExtensionId", "SQL activity targetDataExtensionId is required outside dry-run mode");
        }

        Map<String, DataExtensionField> sourceFields = fieldMap(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(source.getId()));
        validateQueryFields(query, sourceFields);

        List<String> warnings = new ArrayList<>();
        QueryRows queryRows = readRows(tenantId, workspaceId, source.getId(), sourceFields, query, effectiveLimit, warnings);
        List<Map<String, Object>> outputRows = target == null
                ? queryRows.rows()
                : normalizeRowsForTarget(target.getId(), queryRows.rows());

        long rowsWritten = 0L;
        if (!dryRun) {
            rowsWritten = writeRows(tenantId, workspaceId, target, outputRows, writeMode, warnings);
        }

        return DataExtensionDto.SqlQueryActivityResponse.builder()
                .valid(true)
                .sourceDataExtensionId(source.getId())
                .targetDataExtensionId(target == null ? null : target.getId())
                .writeMode(writeMode)
                .dryRun(dryRun)
                .rowsRead(queryRows.rowsRead())
                .rowsWritten(rowsWritten)
                .previewRows(outputRows.stream().limit(10).toList())
                .warnings(warnings)
                .errors(List.of())
                .build();
    }

    private SqlQuery parseSql(String sql) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (trimmed.contains(";") || trimmed.contains("--") || trimmed.contains("/*") || trimmed.contains("*/")) {
            throw new ValidationException("sql", "SQL activity accepts one comment-free SELECT statement");
        }
        List<String> forbidden = List.of("INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "TRUNCATE", "CREATE", "MERGE", "CALL", "EXEC", "GRANT", "REVOKE");
        for (String token : forbidden) {
            if (Pattern.compile("(?i)\\b" + token + "\\b").matcher(upper).find()) {
                throw new ValidationException("sql", "SQL activity contains forbidden token: " + token);
            }
        }

        Matcher select = SELECT_PATTERN.matcher(trimmed);
        if (!select.matches()) {
            throw new ValidationException("sql", "SQL activity supports SELECT <fields> FROM <dataExtension> only");
        }
        List<String> fields = parseProjection(select.group(1));
        String source = normalizeSource(select.group(2));
        Matcher tail = TAIL_PATTERN.matcher(select.group(3));
        if (!tail.matches()) {
            throw new ValidationException("sql", "SQL activity supports WHERE, ORDER BY, and LIMIT in that order");
        }

        List<Condition> conditions = parseConditions(tail.group(1));
        String orderBy = normalizeIdentifier(tail.group(2), "orderBy", true);
        String sortDirection = tail.group(3) == null ? "ASC" : tail.group(3).toUpperCase(Locale.ROOT);
        Integer limit = tail.group(4) == null ? null : Integer.parseInt(tail.group(4));
        if (limit != null && (limit < 1 || limit > MAX_ACTIVITY_ROWS)) {
            throw new ValidationException("limit", "SQL activity LIMIT must be between 1 and " + MAX_ACTIVITY_ROWS);
        }
        return new SqlQuery(fields, source, conditions, orderBy, sortDirection, limit);
    }

    private List<String> parseProjection(String rawFields) {
        String trimmed = rawFields.trim();
        if ("*".equals(trimmed)) {
            return List.of();
        }
        List<String> fields = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            fields.add(normalizeIdentifier(part, "fields", false));
        }
        return fields;
    }

    private List<Condition> parseConditions(String rawWhere) {
        if (blank(rawWhere)) {
            return List.of();
        }
        List<Condition> conditions = new ArrayList<>();
        for (String rawPart : rawWhere.trim().split("(?i)\\s+AND\\s+")) {
            String part = rawPart.trim();
            Matcher nullMatcher = NULL_CONDITION_PATTERN.matcher(part);
            if (nullMatcher.matches()) {
                conditions.add(new Condition(
                        normalizeIdentifier(nullMatcher.group(1), "where", false),
                        nullMatcher.group(2) == null ? "IS_NULL" : "NOT_NULL",
                        null));
                continue;
            }
            Matcher valueMatcher = VALUE_CONDITION_PATTERN.matcher(part);
            if (!valueMatcher.matches()) {
                throw new ValidationException("where", "Unsupported SQL condition: " + part);
            }
            conditions.add(new Condition(
                    normalizeIdentifier(valueMatcher.group(1), "where", false),
                    normalizeOperator(valueMatcher.group(2)),
                    parseLiteral(valueMatcher.group(3))));
        }
        return conditions;
    }

    private Object parseLiteral(String raw) {
        String value = raw.trim();
        if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        try {
            return value.contains(".") ? new BigDecimal(value) : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new ValidationException("where", "SQL literal must be quoted text, boolean, or number");
        }
    }

    private QueryRows readRows(String tenantId,
                               String workspaceId,
                               String sourceId,
                               Map<String, DataExtensionField> sourceFields,
                               SqlQuery query,
                               int effectiveLimit,
                               List<String> warnings) {
        List<Map<String, Object>> matches = new ArrayList<>();
        long rowsRead = 0L;
        int pageIndex = 0;
        while (rowsRead < MAX_SCAN_ROWS) {
            Page<DataExtensionRecord> page = recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId(
                    tenantId,
                    workspaceId,
                    sourceId,
                    PageRequest.of(pageIndex, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }
            for (DataExtensionRecord record : page.getContent()) {
                rowsRead++;
                Map<String, Object> data = record.getRecordData() == null ? Map.of() : record.getRecordData();
                if (matches(data, query.conditions())) {
                    matches.add(project(data, query.fields(), sourceFields));
                }
                if (matches.size() >= effectiveLimit || rowsRead >= MAX_SCAN_ROWS) {
                    break;
                }
            }
            if (matches.size() >= effectiveLimit || !page.hasNext() || page.getContent().isEmpty()) {
                break;
            }
            pageIndex++;
        }
        if (rowsRead >= MAX_SCAN_ROWS) {
            warnings.add("SQL activity scan capped at " + MAX_SCAN_ROWS + " source rows.");
        }
        return new QueryRows(matches.stream()
                .sorted(sortComparator(query.orderBy(), query.sortDirection()))
                .limit(effectiveLimit)
                .toList(), rowsRead);
    }

    private long writeRows(String tenantId,
                           String workspaceId,
                           DataExtension target,
                           List<Map<String, Object>> rows,
                           String writeMode,
                           List<String> warnings) {
        long rowsWritten = switch (writeMode) {
            case "APPEND" -> appendRows(tenantId, workspaceId, target.getId(), rows);
            case "OVERWRITE" -> {
                recordRepository.deleteByTenantIdAndWorkspaceIdAndDataExtensionId(tenantId, workspaceId, target.getId());
                yield appendRows(tenantId, workspaceId, target.getId(), rows);
            }
            case "UPDATE", "UPSERT" -> updateRows(tenantId, workspaceId, target, rows, writeMode, warnings);
            default -> throw new ValidationException("writeMode", "Unsupported write mode: " + writeMode);
        };
        target.setRecordCount(recordRepository.countByTenantWorkspaceAndDataExtension(tenantId, workspaceId, target.getId()));
        deRepository.save(target);
        return rowsWritten;
    }

    private long appendRows(String tenantId, String workspaceId, String targetId, List<Map<String, Object>> rows) {
        List<DataExtensionRecord> records = rows.stream()
                .map(row -> newRecord(tenantId, workspaceId, targetId, row))
                .toList();
        recordRepository.saveAll(records);
        return records.size();
    }

    private long updateRows(String tenantId,
                            String workspaceId,
                            DataExtension target,
                            List<Map<String, Object>> rows,
                            String writeMode,
                            List<String> warnings) {
        String primaryKey = primaryKeyField(target);
        Map<String, DataExtensionRecord> existing = loadTargetIndex(tenantId, workspaceId, target.getId(), primaryKey, warnings);
        List<DataExtensionRecord> toSave = new ArrayList<>();
        long skipped = 0L;
        for (Map<String, Object> row : rows) {
            String key = stringValue(row.get(primaryKey));
            if (blank(key)) {
                throw new ValidationException("primaryKey", "SQL activity row is missing primary key field: " + primaryKey);
            }
            DataExtensionRecord record = existing.get(key);
            if (record == null) {
                if ("UPDATE".equals(writeMode)) {
                    skipped++;
                    continue;
                }
                record = newRecord(tenantId, workspaceId, target.getId(), row);
            } else {
                record.setRecordData(new LinkedHashMap<>(row));
            }
            toSave.add(record);
        }
        if (skipped > 0) {
            warnings.add("UPDATE skipped " + skipped + " rows with no matching target primary key.");
        }
        recordRepository.saveAll(toSave);
        return toSave.size();
    }

    private Map<String, DataExtensionRecord> loadTargetIndex(String tenantId,
                                                             String workspaceId,
                                                             String targetId,
                                                             String primaryKey,
                                                             List<String> warnings) {
        Map<String, DataExtensionRecord> index = new LinkedHashMap<>();
        long scanned = 0L;
        int pageIndex = 0;
        while (scanned < MAX_SCAN_ROWS) {
            Page<DataExtensionRecord> page = recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId(
                    tenantId,
                    workspaceId,
                    targetId,
                    PageRequest.of(pageIndex, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }
            for (DataExtensionRecord record : page.getContent()) {
                scanned++;
                String key = stringValue(record.getRecordData() == null ? null : record.getRecordData().get(primaryKey));
                if (!blank(key)) {
                    index.putIfAbsent(key, record);
                }
                if (scanned >= MAX_SCAN_ROWS) {
                    break;
                }
            }
            if (!page.hasNext() || page.getContent().isEmpty()) {
                break;
            }
            pageIndex++;
        }
        if (scanned >= MAX_SCAN_ROWS) {
            warnings.add("Target primary-key index capped at " + MAX_SCAN_ROWS + " rows.");
        }
        return index;
    }

    private List<Map<String, Object>> normalizeRowsForTarget(String targetId, List<Map<String, Object>> rows) {
        List<DataExtensionField> fields = fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(targetId);
        Map<String, DataExtensionField> fieldMap = fieldMap(fields);
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            normalizedRows.add(normalizeRecord(row, fields, fieldMap));
        }
        return normalizedRows;
    }

    private Map<String, Object> normalizeRecord(Map<String, Object> row,
                                                List<DataExtensionField> fields,
                                                Map<String, DataExtensionField> fieldMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (String key : row.keySet()) {
            if (!fieldMap.containsKey(key)) {
                throw new ValidationException("targetDataExtensionId", "Target data extension does not contain field: " + key);
            }
        }
        for (DataExtensionField field : fields) {
            Object value = row.get(field.getFieldName());
            if ((value == null || String.valueOf(value).isBlank()) && field.getDefaultValue() != null) {
                value = field.getDefaultValue();
            }
            if (field.isRequired() && (value == null || String.valueOf(value).isBlank())) {
                throw new ValidationException(field.getFieldName(), "Target row is missing required field: " + field.getFieldName());
            }
            if (value != null) {
                normalized.put(field.getFieldName(), coerceValue(field, value));
            }
        }
        return normalized;
    }

    private void validateQueryFields(SqlQuery query, Map<String, DataExtensionField> sourceFields) {
        for (String field : query.fields()) {
            if (!sourceFields.containsKey(field)) {
                throw new ValidationException("fields", "Unknown projection field: " + field);
            }
        }
        for (Condition condition : query.conditions()) {
            if (!sourceFields.containsKey(condition.field())) {
                throw new ValidationException("where", "Unknown filter field: " + condition.field());
            }
        }
        if (query.orderBy() != null && !sourceFields.containsKey(query.orderBy())) {
            throw new ValidationException("orderBy", "Unknown sort field: " + query.orderBy());
        }
    }

    private boolean matches(Map<String, Object> data, List<Condition> conditions) {
        for (Condition condition : conditions) {
            Object actual = data.get(condition.field());
            Object expected = condition.value();
            boolean matched = switch (condition.operator()) {
                case "EQUALS" -> Objects.equals(stringValue(actual), stringValue(expected));
                case "NOT_EQUALS" -> !Objects.equals(stringValue(actual), stringValue(expected));
                case "IS_NULL" -> actual == null;
                case "NOT_NULL" -> actual != null;
                case "GT" -> compare(actual, expected) > 0;
                case "GTE" -> compare(actual, expected) >= 0;
                case "LT" -> compare(actual, expected) < 0;
                case "LTE" -> compare(actual, expected) <= 0;
                case "LIKE" -> like(stringValue(actual), stringValue(expected));
                default -> throw new ValidationException("where", "Unsupported operator: " + condition.operator());
            };
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> project(Map<String, Object> data,
                                        List<String> requestedFields,
                                        Map<String, DataExtensionField> sourceFields) {
        List<String> projection = requestedFields.isEmpty() ? sourceFields.keySet().stream().toList() : requestedFields;
        Map<String, Object> row = new LinkedHashMap<>();
        for (String field : projection) {
            row.put(field, data.get(field));
        }
        return row;
    }

    private Comparator<Map<String, Object>> sortComparator(String field, String direction) {
        if (field == null) {
            return (left, right) -> 0;
        }
        Comparator<Map<String, Object>> comparator = (left, right) -> compare(left.get(field), right.get(field));
        return "DESC".equalsIgnoreCase(direction) ? comparator.reversed() : comparator;
    }

    private Object coerceValue(DataExtensionField field, Object value) {
        String raw = String.valueOf(value);
        return switch (field.getFieldType()) {
            case TEXT, PHONE, LOCALE -> validateLength(field, raw);
            case EMAIL -> {
                String normalized = validateLength(field, raw).toLowerCase(Locale.ROOT);
                if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                    throw new ValidationException(field.getFieldName(), "Value must be a valid email address");
                }
                yield normalized;
            }
            case NUMBER -> {
                try {
                    yield Long.parseLong(raw);
                } catch (NumberFormatException ex) {
                    throw new ValidationException(field.getFieldName(), "Value must be an integer");
                }
            }
            case DECIMAL -> {
                try {
                    yield new BigDecimal(raw);
                } catch (NumberFormatException ex) {
                    throw new ValidationException(field.getFieldName(), "Value must be decimal");
                }
            }
            case BOOLEAN -> {
                if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
                    yield Boolean.parseBoolean(raw);
                }
                throw new ValidationException(field.getFieldName(), "Value must be true or false");
            }
            case DATE, DATETIME -> {
                try {
                    Instant.parse(raw.contains("T") ? raw : raw + "T00:00:00Z");
                    yield raw;
                } catch (DateTimeParseException ex) {
                    throw new ValidationException(field.getFieldName(), "Value must be ISO date or datetime");
                }
            }
        };
    }

    private String primaryKeyField(DataExtension target) {
        String configured = trimToNull(target.getPrimaryKeyField());
        if (configured != null) {
            return configured;
        }
        return fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(target.getId())
                .stream()
                .filter(DataExtensionField::isPrimaryKey)
                .map(DataExtensionField::getFieldName)
                .findFirst()
                .orElseThrow(() -> new ValidationException("primaryKeyField", "UPDATE/UPSERT requires target primary key field"));
    }

    private DataExtensionRecord newRecord(String tenantId, String workspaceId, String targetId, Map<String, Object> row) {
        DataExtensionRecord record = new DataExtensionRecord();
        record.setTenantId(tenantId);
        record.setWorkspaceId(workspaceId);
        record.setDataExtensionId(targetId);
        record.setRecordData(new LinkedHashMap<>(row));
        return record;
    }

    private DataExtension requireSource(String tenantId, String workspaceId, String source) {
        return deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, source)
                .or(() -> deRepository.findByTenantIdAndWorkspaceIdAndNameIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, source))
                .orElseThrow(() -> new NotFoundException("DataExtension", source));
    }

    private DataExtension requireDataExtension(String tenantId, String workspaceId, String id) {
        return deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("DataExtension", id));
    }

    private Map<String, DataExtensionField> fieldMap(List<DataExtensionField> fields) {
        Map<String, DataExtensionField> map = new LinkedHashMap<>();
        for (DataExtensionField field : fields) {
            map.put(field.getFieldName(), field);
        }
        return map;
    }

    private String normalizeSource(String raw) {
        String value = stripQuotes(raw.trim());
        if (!SOURCE_PATTERN.matcher(value).matches()) {
            throw new ValidationException("source", "Invalid data extension source: " + raw);
        }
        return value;
    }

    private String normalizeIdentifier(String raw, String field, boolean optional) {
        if (raw == null || raw.isBlank()) {
            if (optional) {
                return null;
            }
            throw new ValidationException(field, "SQL identifier is required");
        }
        String value = stripQuotes(raw.trim());
        if (!IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new ValidationException(field, "Invalid SQL identifier: " + raw);
        }
        return value;
    }

    private String stripQuotes(String raw) {
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("`") && raw.endsWith("`"))) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private String normalizeOperator(String raw) {
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "=" -> "EQUALS";
            case "!=", "<>" -> "NOT_EQUALS";
            case ">" -> "GT";
            case ">=" -> "GTE";
            case "<" -> "LT";
            case "<=" -> "LTE";
            case "LIKE" -> "LIKE";
            default -> throw new ValidationException("where", "Unsupported operator: " + raw);
        };
    }

    private String normalizeWriteMode(String raw) {
        String mode = trimToNull(raw) == null ? "APPEND" : raw.trim().toUpperCase(Locale.ROOT);
        if (!WRITE_MODES.contains(mode)) {
            throw new ValidationException("writeMode", "Unsupported write mode: " + raw);
        }
        return mode;
    }

    private int clampMaxRows(Integer value) {
        if (value == null) {
            return MAX_ACTIVITY_ROWS;
        }
        return Math.max(1, Math.min(value, MAX_ACTIVITY_ROWS));
    }

    private int compare(Object left, Object right) {
        try {
            return new BigDecimal(stringValue(left)).compareTo(new BigDecimal(stringValue(right)));
        } catch (NumberFormatException ex) {
            return stringValue(left).compareTo(stringValue(right));
        }
    }

    private boolean like(String actual, String pattern) {
        String normalized = pattern.replace("%", "");
        if (pattern.startsWith("%") && pattern.endsWith("%")) {
            return actual.contains(normalized);
        }
        if (pattern.startsWith("%")) {
            return actual.endsWith(normalized);
        }
        if (pattern.endsWith("%")) {
            return actual.startsWith(normalized);
        }
        return actual.equals(pattern);
    }

    private String validateLength(DataExtensionField field, String value) {
        if (field.getMaxLength() != null && value.length() > field.getMaxLength()) {
            throw new ValidationException(field.getFieldName(), "Value exceeds maxLength " + field.getMaxLength());
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record SqlQuery(List<String> fields,
                            String source,
                            List<Condition> conditions,
                            String orderBy,
                            String sortDirection,
                            Integer limit) {
    }

    private record Condition(String field, String operator, Object value) {
    }

    private record QueryRows(List<Map<String, Object>> rows, long rowsRead) {
    }
}
