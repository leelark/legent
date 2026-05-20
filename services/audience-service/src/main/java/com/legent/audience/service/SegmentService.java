package com.legent.audience.service;

import com.legent.audience.domain.Segment;
import com.legent.audience.dto.SegmentDto;
import com.legent.audience.mapper.SegmentMapper;
import com.legent.audience.repository.SegmentRepository;
import com.legent.audience.event.SegmentEventPublisher;
import com.legent.common.exception.ConflictException;
import com.legent.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor

public class SegmentService {

    private static final Set<String> SUPPORTED_CONDITION_OPERATORS = Set.of(
            "EQUALS",
            "NOT_EQUALS",
            "CONTAINS",
            "STARTS_WITH",
            "ENDS_WITH",
            "GREATER_THAN",
            "LESS_THAN",
            "IS_NULL",
            "IS_NOT_NULL",
            "IN_LIST",
            "NOT_IN_LIST",
            "IN_SEGMENT");
    private static final Set<String> LIST_MEMBERSHIP_OPERATORS = Set.of("IN_LIST", "NOT_IN_LIST");

    private final SegmentRepository segmentRepository;
    private final SegmentMapper segmentMapper;
    private final SegmentEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<SegmentDto.Response> list(Pageable pageable) {
        return segmentRepository.findAllByTenantAndWorkspace(AudienceScope.tenantId(), AudienceScope.workspaceId(), pageable)
                .map(segmentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SegmentDto.Response getById(String id) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Segment segment = segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("Segment", id));
        return segmentMapper.toResponse(segment);
    }

    @Transactional
    public SegmentDto.Response create(SegmentDto.CreateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        if (segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(tenantId, workspaceId, request.getName())) {
            throw new ConflictException("Segment", "name", request.getName());
        }

        Segment entity = segmentMapper.toEntity(request);
        entity.setTenantId(tenantId);
        entity.setWorkspaceId(workspaceId);
        if (request.getSegmentType() != null) {
            entity.setSegmentType(parseSegmentType(request.getSegmentType()));
        }
        normalizeForPersist(entity);
        validateRules(entity.getRules());

        Segment saved = segmentRepository.save(entity);
        log.info("Segment created: name={}, id={}", saved.getName(), saved.getId());
        eventPublisher.publishCreated(saved);
        return segmentMapper.toResponse(saved);
    }

    @Transactional
    public SegmentDto.Response update(String id, SegmentDto.UpdateRequest request) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Segment existing = segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("Segment", id));

        if (request.getName() != null) existing.setName(request.getName());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getRules() != null) existing.setRules(request.getRules());
        if (request.getScheduleEnabled() != null) existing.setScheduleEnabled(request.getScheduleEnabled());
        if (request.getStatus() != null) existing.setStatus(Segment.SegmentStatus.valueOf(request.getStatus().toUpperCase()));
        normalizeForPersist(existing);
        validateRules(existing.getRules());

        Segment saved = segmentRepository.save(existing);
        eventPublisher.publishUpdated(saved);
        return segmentMapper.toResponse(saved);
    }

    @Transactional
    public void delete(String id) {
        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        Segment existing = segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(tenantId, workspaceId, id)
                .orElseThrow(() -> new NotFoundException("Segment", id));
        existing.softDelete();
        segmentRepository.save(existing);
        log.info("Segment deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public long count() {
        return segmentRepository.countByTenantAndWorkspace(AudienceScope.tenantId(), AudienceScope.workspaceId());
    }

    private Segment.SegmentType parseSegmentType(String segmentType) {
        if (segmentType == null || segmentType.isBlank()) {
            return Segment.SegmentType.FILTER;
        }
        String normalized = segmentType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FILTER", "DYNAMIC", "DYNAMIC_LIST" -> Segment.SegmentType.FILTER;
            case "MANUAL", "STATIC", "STATIC_LIST" -> Segment.SegmentType.MANUAL;
            case "QUERY" -> Segment.SegmentType.QUERY;
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid segmentType: " + segmentType + ". Allowed values: FILTER, QUERY, MANUAL, DYNAMIC, STATIC");
        };
    }

    private void normalizeForPersist(Segment segment) {
        if (segment.getOwnershipScope() == null || segment.getOwnershipScope().isBlank()) {
            segment.setOwnershipScope("WORKSPACE");
        }
        if (segment.getRules() == null) {
            segment.setRules(defaultRules());
        }
        if (segment.getTags() == null) {
            segment.setTags(new ArrayList<>());
        }
        if (segment.getStatus() == null) {
            segment.setStatus(Segment.SegmentStatus.DRAFT);
        }
        if (segment.getSegmentType() == null) {
            segment.setSegmentType(Segment.SegmentType.FILTER);
        }
    }

    private LinkedHashMap<String, Object> defaultRules() {
        LinkedHashMap<String, Object> rules = new LinkedHashMap<>();
        rules.put("operator", "AND");
        rules.put("conditions", new ArrayList<>());
        rules.put("groups", new ArrayList<>());
        return rules;
    }

    private void validateRules(Map<String, Object> rules) {
        validateRuleGroup(rules, "rules");
    }

    private void validateRuleGroup(Map<String, Object> group, String path) {
        if (group == null) {
            throw new IllegalArgumentException(path + " is required");
        }

        String operator = stringValueOrDefault(group.get("operator"), "AND", path + ".operator")
                .toUpperCase(Locale.ROOT);
        if (!"AND".equals(operator) && !"OR".equals(operator)) {
            throw new IllegalArgumentException(path + ".operator must be AND or OR");
        }

        List<?> conditions = optionalList(group.get("conditions"), path + ".conditions");
        for (int i = 0; i < conditions.size(); i++) {
            Object condition = conditions.get(i);
            if (!(condition instanceof Map<?, ?> conditionMap)) {
                throw new IllegalArgumentException(path + ".conditions[" + i + "] must be an object");
            }
            validateCondition(conditionMap, path + ".conditions[" + i + "]");
        }

        List<?> groups = optionalList(group.get("groups"), path + ".groups");
        for (int i = 0; i < groups.size(); i++) {
            Object child = groups.get(i);
            if (!(child instanceof Map<?, ?> childMap)) {
                throw new IllegalArgumentException(path + ".groups[" + i + "] must be an object");
            }
            validateRuleGroup(asStringObjectMap(childMap, path + ".groups[" + i + "]"), path + ".groups[" + i + "]");
        }
    }

    private void validateCondition(Map<?, ?> condition, String path) {
        String field = requiredString(condition.get("field"), path + ".field");
        String operator = requiredString(condition.get("op"), path + ".op").toUpperCase(Locale.ROOT);

        validateFieldName(field, path + ".field");

        if (!SUPPORTED_CONDITION_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("Unsupported segment operator: " + operator);
        }

        boolean listMembershipField = "list_membership".equalsIgnoreCase(field);
        if (LIST_MEMBERSHIP_OPERATORS.contains(operator)) {
            if (!listMembershipField) {
                throw new IllegalArgumentException("List membership operators require list_membership field");
            }
            requiredString(condition.get("value"), path + ".value");
            return;
        }

        if (listMembershipField) {
            throw new IllegalArgumentException("list_membership only supports IN_LIST and NOT_IN_LIST operators");
        }
    }

    private List<?> optionalList(Object value, String fieldName) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(fieldName + " must be an array");
        }
        return list;
    }

    private String requiredString(Object value, String fieldName) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return text.trim();
    }

    private String stringValueOrDefault(Object value, String defaultValue, String fieldName) {
        if (value == null) {
            return defaultValue;
        }
        return requiredString(value, fieldName);
    }

    private Map<String, Object> asStringObjectMap(Map<?, ?> value, String fieldName) {
        LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException(fieldName + " keys must be strings");
            }
            converted.put(key, entry.getValue());
        }
        return converted;
    }

    private void validateFieldName(String field, String fieldName) {
        if (!field.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException(fieldName + " contains invalid characters");
        }
        if (field.length() > 64) {
            throw new IllegalArgumentException(fieldName + " is too long");
        }
        if (isSqlKeyword(field.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(fieldName + " is reserved");
        }
    }

    private boolean isSqlKeyword(String field) {
        return switch (field) {
            case "select", "insert", "update", "delete", "drop", "create", "alter",
                 "where", "and", "or", "not", "null", "true", "false",
                 "union", "join", "from", "table", "column", "database",
                 "exec", "execute", "script", "eval", "cast", "convert" -> true;
            default -> false;
        };
    }
}
