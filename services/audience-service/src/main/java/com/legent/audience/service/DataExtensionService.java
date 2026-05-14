package com.legent.audience.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.audience.domain.DataExtension;
import com.legent.audience.domain.DataExtensionField;
import com.legent.audience.domain.DataExtensionRecord;
import com.legent.audience.dto.DataExtensionDto;
import com.legent.audience.repository.DataExtensionFieldRepository;
import com.legent.audience.repository.DataExtensionRecordRepository;
import com.legent.audience.repository.DataExtensionRepository;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataExtensionService {

    private static final TypeReference<List<DataExtensionDto.RelationshipDefinition>> RELATIONSHIP_LIST_TYPE = new TypeReference<>() {};
    private static final int MAX_PREVIEW_LIMIT = 500;

    private final DataExtensionRepository deRepository;
    private final DataExtensionFieldRepository fieldRepository;
    private final DataExtensionRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<DataExtensionDto.Response> list(Pageable pageable) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        return deRepository.findAllByTenantAndWorkspace(tenantId, workspaceId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DataExtensionDto.Response getById(String id) {
        DataExtension de = requireDataExtension(id);
        return toResponse(de);
    }

    @Transactional
    public DataExtensionDto.Response create(DataExtensionDto.CreateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        validateFieldDefinitions(request.getFields());
        if (deRepository.existsByTenantWorkspaceAndName(tenantId, workspaceId, request.getName())) {
            throw new ConflictException("DataExtension", "name", request.getName());
        }

        DataExtension de = new DataExtension();
        de.setTenantId(tenantId);
        de.setWorkspaceId(workspaceId);
        de.setName(request.getName());
        de.setDescription(request.getDescription());
        de.setSendable(request.isSendable());
        de.setSendableField(normalizeBlank(request.getSendableField()));
        de.setPrimaryKeyField(normalizeBlank(request.getPrimaryKeyField()));
        validateSendableFields(request.isSendable(), de.getSendableField(), de.getPrimaryKeyField(), request.getFields());
        DataExtension savedDe = deRepository.save(de);

        saveFields(savedDe.getId(), request.getFields(), true);

        log.info("Data extension created: tenant={}, name={}, id={}, fields={}",
                tenantId, de.getName(), savedDe.getId(), request.getFields().size());
        return toResponse(savedDe);
    }

    @Transactional
    public DataExtensionDto.Response updateSchema(String deId, DataExtensionDto.SchemaUpdateRequest request) {
        DataExtension de = requireDataExtension(deId);
        validateFieldDefinitions(request.getFields());
        boolean replaceExisting = request.getReplaceExisting() == null || request.getReplaceExisting();
        if (de.getRecordCount() > 0 && replaceExisting) {
            ensureCompatibleSchemaChange(de.getId(), request.getFields());
        }
        validateSendableFields(de.isSendable(), de.getSendableField(), de.getPrimaryKeyField(), request.getFields());
        saveFields(de.getId(), request.getFields(), replaceExisting);
        return toResponse(deRepository.save(de));
    }

    @Transactional
    public DataExtensionDto.Response updateSendableConfig(String deId, DataExtensionDto.SendableConfigRequest request) {
        DataExtension de = requireDataExtension(deId);
        boolean sendable = request.getSendable() == null ? de.isSendable() : request.getSendable();
        String sendableField = request.getSendableField() == null ? de.getSendableField() : normalizeBlank(request.getSendableField());
        String primaryKeyField = request.getPrimaryKeyField() == null ? de.getPrimaryKeyField() : normalizeBlank(request.getPrimaryKeyField());
        validateSendableFields(sendable, sendableField, primaryKeyField, toFieldDefinitions(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(deId)));
        de.setSendable(sendable);
        de.setSendableField(sendableField);
        de.setPrimaryKeyField(primaryKeyField);
        return toResponse(deRepository.save(de));
    }

    @Transactional
    public DataExtensionDto.Response updateRetentionPolicy(String deId, DataExtensionDto.RetentionPolicyRequest request) {
        DataExtension de = requireDataExtension(deId);
        String action = request.getRetentionAction() == null ? "NONE" : request.getRetentionAction().trim().toUpperCase(Locale.ROOT);
        if ("NONE".equals(action)) {
            de.setRetentionDays(null);
            de.setRetentionAction("NONE");
        } else {
            if (request.getRetentionDays() == null || request.getRetentionDays() < 1) {
                throw new ValidationException("retentionDays", "retentionDays is required when retention action is enabled");
            }
            de.setRetentionDays(request.getRetentionDays());
            de.setRetentionAction(action);
        }
        return toResponse(deRepository.save(de));
    }

    @Transactional
    public DataExtensionDto.Response updateRelationships(String deId, DataExtensionDto.RelationshipRequest request) {
        DataExtension de = requireDataExtension(deId);
        List<DataExtensionDto.RelationshipDefinition> relationships = request.getRelationships() == null
                ? List.of()
                : request.getRelationships();
        validateRelationships(de, relationships);
        de.setRelationshipJson(writeRelationships(relationships));
        return toResponse(deRepository.save(de));
    }

    @Transactional
    public DataExtensionDto.RecordResponse addRecord(String deId, DataExtensionDto.RecordRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        DataExtension de = requireDataExtension(deId);

        Map<String, Object> normalizedData = validateRecord(de.getId(), request.getData());

        DataExtensionRecord record = new DataExtensionRecord();
        record.setTenantId(tenantId);
        record.setWorkspaceId(workspaceId);
        record.setDataExtensionId(deId);
        record.setRecordData(normalizedData);
        DataExtensionRecord saved = recordRepository.save(record);

        de.setRecordCount(recordRepository.countByTenantWorkspaceAndDataExtension(tenantId, workspaceId, deId));
        deRepository.save(de);

        return mapRecord(saved);
    }

    @Transactional(readOnly = true)
    public Page<DataExtensionDto.RecordResponse> listRecords(String deId, Pageable pageable) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        requireDataExtension(deId);
        return recordRepository.findByTenantIdAndWorkspaceIdAndDataExtensionId(tenantId, workspaceId, deId, pageable)
                .map(this::mapRecord);
    }

    @Transactional(readOnly = true)
    public DataExtensionDto.QueryPreviewResponse previewQuery(String deId, DataExtensionDto.QueryPreviewRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        DataExtension de = requireDataExtension(deId);
        List<DataExtensionField> fields = fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(de.getId());
        Map<String, DataExtensionField> fieldMap = fieldMap(fields);
        DataExtensionDto.QueryPreviewRequest safeRequest = request == null ? new DataExtensionDto.QueryPreviewRequest() : request;
        List<String> warnings = new ArrayList<>();
        int limit = Math.min(safeRequest.getLimit() == null ? 100 : safeRequest.getLimit(), MAX_PREVIEW_LIMIT);

        List<DataExtensionRecord> records = recordRepository
                .findByTenantIdAndWorkspaceIdAndDataExtensionId(tenantId, workspaceId, deId, PageRequest.of(0, limit))
                .getContent();

        List<Map<String, Object>> rows = records.stream()
                .map(DataExtensionRecord::getRecordData)
                .filter(data -> matchesFilters(data, safeRequest.getFilters(), fieldMap))
                .map(data -> projectFields(data, safeRequest.getFields(), fieldMap))
                .sorted(sortComparator(safeRequest.getSortField(), safeRequest.getSortDirection()))
                .limit(limit)
                .toList();

        long total = recordRepository.countByTenantWorkspaceAndDataExtension(tenantId, workspaceId, deId);
        if (total > limit) {
            warnings.add("Preview is capped at " + limit + " rows; use exports for full result sets.");
        }

        return DataExtensionDto.QueryPreviewResponse.builder()
                .rows(rows)
                .returnedRows(rows.size())
                .scannedRows(total)
                .warnings(warnings)
                .build();
    }

    @Transactional(readOnly = true)
    public DataExtensionDto.ImportMappingPreviewResponse previewImportMapping(String deId,
                                                                              DataExtensionDto.ImportMappingPreviewRequest request) {
        DataExtension de = requireDataExtension(deId);
        List<DataExtensionField> fields = fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(de.getId());
        Map<String, DataExtensionField> fieldMap = fieldMap(fields);
        Map<String, String> normalizedMapping = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, String> rawMapping = request.getFieldMapping() == null ? Map.of() : request.getFieldMapping();
        Set<String> sourceHeaders = new HashSet<>(request.getSourceHeaders() == null ? List.of() : request.getSourceHeaders());
        for (Map.Entry<String, String> entry : rawMapping.entrySet()) {
            String targetField = normalizeBlank(entry.getKey());
            String sourceField = normalizeBlank(entry.getValue());
            if (targetField == null || sourceField == null) {
                continue;
            }
            if (!fieldMap.containsKey(targetField)) {
                errors.add("Mapped target field does not exist: " + targetField);
                continue;
            }
            if (!sourceHeaders.isEmpty() && !sourceHeaders.contains(sourceField)) {
                errors.add("Mapped source header missing from upload: " + sourceField);
            }
            normalizedMapping.put(targetField, sourceField);
        }

        for (DataExtensionField field : fields) {
            if (field.isRequired() && !normalizedMapping.containsKey(field.getFieldName())) {
                errors.add("Required field is not mapped: " + field.getFieldName());
            }
        }
        for (String header : sourceHeaders) {
            if (!normalizedMapping.containsValue(header)) {
                warnings.add("Source header is not mapped and will be ignored: " + header);
            }
        }

        List<Map<String, Object>> sampleMappedRows = new ArrayList<>();
        List<Map<String, Object>> sampleRows = request.getSampleRows() == null ? List.of() : request.getSampleRows();
        for (Map<String, Object> sourceRow : sampleRows.stream().limit(10).toList()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (Map.Entry<String, String> mapping : normalizedMapping.entrySet()) {
                mapped.put(mapping.getKey(), sourceRow.get(mapping.getValue()));
            }
            try {
                sampleMappedRows.add(validateRecord(deId, mapped));
            } catch (ValidationException ex) {
                errors.add(ex.getMessage());
                sampleMappedRows.add(mapped);
            }
        }

        return DataExtensionDto.ImportMappingPreviewResponse.builder()
                .valid(errors.isEmpty())
                .normalizedMapping(normalizedMapping)
                .sampleMappedRows(sampleMappedRows)
                .errors(errors)
                .warnings(warnings)
                .build();
    }

    @Transactional
    public void deleteDataExtension(String id) {
        DataExtension de = requireDataExtension(id);
        de.softDelete();
        deRepository.save(de);
        log.info("Data extension deleted: tenant={}, id={}", TenantContext.requireTenantId(), id);
    }

    @Transactional(readOnly = true)
    public long count() {
        return deRepository.countByTenantAndWorkspace(AudienceScope.tenantId(), AudienceScope.workspaceId());
    }

    private DataExtension requireDataExtension(String id) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        return deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("DataExtension", id));
    }

    private void saveFields(String deId, List<DataExtensionDto.FieldDefinition> definitions, boolean replaceExisting) {
        if (replaceExisting) {
            fieldRepository.deleteByDataExtensionId(deId);
        }
        int ordinal = 0;
        for (DataExtensionDto.FieldDefinition fd : definitions) {
            DataExtensionField field = new DataExtensionField();
            field.setDataExtensionId(deId);
            field.setFieldName(fd.getFieldName().trim());
            field.setFieldType(parseFieldType(fd.getFieldType()));
            field.setRequired(fd.isRequired());
            field.setPrimaryKey(fd.isPrimaryKey());
            field.setDefaultValue(fd.getDefaultValue());
            field.setMaxLength(fd.getMaxLength());
            field.setOrdinal(fd.getOrdinal() > 0 ? fd.getOrdinal() : ordinal);
            fieldRepository.save(field);
            ordinal++;
        }
    }

    private void validateFieldDefinitions(List<DataExtensionDto.FieldDefinition> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new ValidationException("fields", "At least one field is required");
        }
        Set<String> names = new HashSet<>();
        int primaryKeys = 0;
        for (DataExtensionDto.FieldDefinition field : fields) {
            String name = normalizeBlank(field.getFieldName());
            if (name == null) {
                throw new ValidationException("fieldName", "Field name is required");
            }
            if (!name.matches("^[A-Za-z][A-Za-z0-9_]{0,127}$")) {
                throw new ValidationException("fieldName", "Use letters, numbers, and underscores; start with a letter");
            }
            if (!names.add(name)) {
                throw new ValidationException("fieldName", "Duplicate field name: " + name);
            }
            parseFieldType(field.getFieldType());
            if (field.getMaxLength() != null && field.getMaxLength() < 1) {
                throw new ValidationException("maxLength", "maxLength must be positive");
            }
            if (field.isPrimaryKey()) {
                primaryKeys++;
            }
        }
        if (primaryKeys > 1) {
            throw new ValidationException("primaryKey", "Only one primary key field is supported per data extension");
        }
    }

    private void validateSendableFields(boolean sendable,
                                        String sendableField,
                                        String primaryKeyField,
                                        List<DataExtensionDto.FieldDefinition> fields) {
        if (!sendable) {
            return;
        }
        if (sendableField == null || sendableField.isBlank()) {
            throw new ValidationException("sendableField", "Sendable data extensions require a sendable field");
        }
        Set<String> fieldNames = new HashSet<>();
        for (DataExtensionDto.FieldDefinition field : fields) {
            fieldNames.add(field.getFieldName());
        }
        if (!fieldNames.contains(sendableField)) {
            throw new ValidationException("sendableField", "sendableField must reference an existing field");
        }
        if (primaryKeyField != null && !primaryKeyField.isBlank() && !fieldNames.contains(primaryKeyField)) {
            throw new ValidationException("primaryKeyField", "primaryKeyField must reference an existing field");
        }
    }

    private void ensureCompatibleSchemaChange(String deId, List<DataExtensionDto.FieldDefinition> fields) {
        List<DataExtensionField> existing = fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(deId);
        Set<String> newFields = new HashSet<>();
        for (DataExtensionDto.FieldDefinition field : fields) {
            newFields.add(field.getFieldName());
        }
        for (DataExtensionField existingField : existing) {
            if (existingField.isRequired() && !newFields.contains(existingField.getFieldName())) {
                throw new ValidationException("fields", "Cannot remove required field while records exist: " + existingField.getFieldName());
            }
        }
    }

    private void validateRelationships(DataExtension source,
                                       List<DataExtensionDto.RelationshipDefinition> relationships) {
        Map<String, DataExtensionField> sourceFields = fieldMap(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(source.getId()));
        Set<String> names = new HashSet<>();
        for (DataExtensionDto.RelationshipDefinition relationship : relationships) {
            String name = normalizeBlank(relationship.getName());
            if (name == null || !names.add(name)) {
                throw new ValidationException("relationships", "Relationship names must be unique and non-empty");
            }
            if (!sourceFields.containsKey(relationship.getSourceField())) {
                throw new ValidationException("sourceField", "Relationship source field does not exist: " + relationship.getSourceField());
            }
            DataExtension target = deRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(
                            source.getTenantId(), source.getWorkspaceId(), relationship.getTargetDataExtensionId())
                    .orElseThrow(() -> new NotFoundException("Target DataExtension", relationship.getTargetDataExtensionId()));
            Map<String, DataExtensionField> targetFields = fieldMap(fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(target.getId()));
            if (!targetFields.containsKey(relationship.getTargetField())) {
                throw new ValidationException("targetField", "Relationship target field does not exist: " + relationship.getTargetField());
            }
        }
    }

    private Map<String, Object> validateRecord(String deId, Map<String, Object> data) {
        if (data == null) {
            throw new ValidationException("data", "Record data is required");
        }
        List<DataExtensionField> fields = fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(deId);
        Map<String, DataExtensionField> fieldMap = fieldMap(fields);
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (String key : data.keySet()) {
            if (!fieldMap.containsKey(key)) {
                throw new ValidationException("data", "Unknown field: " + key);
            }
        }
        for (DataExtensionField field : fields) {
            Object value = data.get(field.getFieldName());
            if ((value == null || String.valueOf(value).isBlank()) && field.getDefaultValue() != null) {
                value = field.getDefaultValue();
            }
            if (field.isRequired() && (value == null || String.valueOf(value).isBlank())) {
                throw new ValidationException("Missing required field: " + field.getFieldName());
            }
            if (value != null) {
                normalized.put(field.getFieldName(), coerceValue(field, value));
            }
        }
        return normalized;
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

    private String validateLength(DataExtensionField field, String value) {
        if (field.getMaxLength() != null && value.length() > field.getMaxLength()) {
            throw new ValidationException(field.getFieldName(), "Value exceeds maxLength " + field.getMaxLength());
        }
        return value;
    }

    private boolean matchesFilters(Map<String, Object> data,
                                   List<DataExtensionDto.QueryFilter> filters,
                                   Map<String, DataExtensionField> fields) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (DataExtensionDto.QueryFilter filter : filters) {
            if (!fields.containsKey(filter.getFieldName())) {
                throw new ValidationException("filter.fieldName", "Unknown field: " + filter.getFieldName());
            }
            Object actual = data.get(filter.getFieldName());
            String operator = filter.getOperator() == null ? "EQUALS" : filter.getOperator().trim().toUpperCase(Locale.ROOT);
            Object expected = filter.getValue();
            boolean matched = switch (operator) {
                case "EQUALS" -> Objects.equals(stringValue(actual), stringValue(expected));
                case "NOT_EQUALS" -> !Objects.equals(stringValue(actual), stringValue(expected));
                case "CONTAINS" -> stringValue(actual).contains(stringValue(expected));
                case "STARTS_WITH" -> stringValue(actual).startsWith(stringValue(expected));
                case "ENDS_WITH" -> stringValue(actual).endsWith(stringValue(expected));
                case "IS_NULL" -> actual == null;
                case "NOT_NULL" -> actual != null;
                case "GT" -> compare(actual, expected) > 0;
                case "GTE" -> compare(actual, expected) >= 0;
                case "LT" -> compare(actual, expected) < 0;
                case "LTE" -> compare(actual, expected) <= 0;
                default -> throw new ValidationException("filter.operator", "Unsupported operator: " + operator);
            };
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> projectFields(Map<String, Object> data,
                                              List<String> requestedFields,
                                              Map<String, DataExtensionField> fields) {
        List<String> projection = requestedFields == null || requestedFields.isEmpty()
                ? fields.keySet().stream().toList()
                : requestedFields;
        Map<String, Object> row = new LinkedHashMap<>();
        for (String field : projection) {
            if (!fields.containsKey(field)) {
                throw new ValidationException("fields", "Unknown projection field: " + field);
            }
            row.put(field, data.get(field));
        }
        return row;
    }

    private Comparator<Map<String, Object>> sortComparator(String field, String direction) {
        if (field == null || field.isBlank()) {
            return (left, right) -> 0;
        }
        Comparator<Map<String, Object>> comparator = Comparator.comparing(row -> stringValue(row.get(field)));
        if ("DESC".equalsIgnoreCase(direction)) {
            return comparator.reversed();
        }
        return comparator;
    }

    private int compare(Object left, Object right) {
        try {
            return new BigDecimal(stringValue(left)).compareTo(new BigDecimal(stringValue(right)));
        } catch (NumberFormatException ex) {
            return stringValue(left).compareTo(stringValue(right));
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private DataExtensionField.FieldType parseFieldType(String fieldType) {
        if (fieldType == null || fieldType.isBlank()) {
            return DataExtensionField.FieldType.TEXT;
        }
        try {
            return DataExtensionField.FieldType.valueOf(fieldType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("fieldType", "Unsupported data extension field type: " + fieldType);
        }
    }

    private DataExtensionDto.RecordResponse mapRecord(DataExtensionRecord record) {
        return DataExtensionDto.RecordResponse.builder()
                .id(record.getId())
                .dataExtensionId(record.getDataExtensionId())
                .data(record.getRecordData())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private DataExtensionDto.Response toResponse(DataExtension de) {
        List<DataExtensionField> fields = fieldRepository.findByDataExtensionIdOrderByOrdinalAsc(de.getId());
        List<DataExtensionDto.FieldDefinition> fieldDtos = toFieldDefinitions(fields);

        return DataExtensionDto.Response.builder()
                .id(de.getId()).name(de.getName()).description(de.getDescription())
                .sendable(de.isSendable()).sendableField(de.getSendableField())
                .primaryKeyField(de.getPrimaryKeyField())
                .retentionDays(de.getRetentionDays())
                .retentionAction(de.getRetentionAction())
                .relationships(readRelationships(de.getRelationshipJson()))
                .recordCount(de.getRecordCount())
                .fields(fieldDtos)
                .createdAt(de.getCreatedAt()).updatedAt(de.getUpdatedAt()).build();
    }

    private List<DataExtensionDto.FieldDefinition> toFieldDefinitions(List<DataExtensionField> fields) {
        return fields.stream()
                .map(f -> DataExtensionDto.FieldDefinition.builder()
                        .fieldName(f.getFieldName()).fieldType(f.getFieldType().name())
                        .required(f.isRequired()).primaryKey(f.isPrimaryKey())
                        .defaultValue(f.getDefaultValue()).maxLength(f.getMaxLength())
                        .ordinal(f.getOrdinal()).build())
                .toList();
    }

    private Map<String, DataExtensionField> fieldMap(List<DataExtensionField> fields) {
        Map<String, DataExtensionField> map = new LinkedHashMap<>();
        for (DataExtensionField field : fields) {
            map.put(field.getFieldName(), field);
        }
        return map;
    }

    private String writeRelationships(List<DataExtensionDto.RelationshipDefinition> relationships) {
        try {
            return objectMapper.writeValueAsString(relationships == null ? List.of() : relationships);
        } catch (Exception ex) {
            throw new ValidationException("relationships", "Unable to serialize relationships");
        }
    }

    private List<DataExtensionDto.RelationshipDefinition> readRelationships(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, RELATIONSHIP_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
