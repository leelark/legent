package com.legent.content.service;

import com.legent.common.exception.NotFoundException;
import com.legent.common.exception.ValidationException;
import com.legent.content.domain.DynamicContentRule;
import com.legent.content.dto.EmailStudioDto;
import com.legent.content.repository.DynamicContentRuleRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DynamicContentRuleService {

    private final DynamicContentRuleRepository ruleRepository;

    @Transactional
    public DynamicContentRule create(String tenantId, String templateId, EmailStudioDto.DynamicRuleRequest request) {
        return create(tenantId, TenantContext.requireWorkspaceId(), templateId, request);
    }

    @Transactional
    public DynamicContentRule create(String tenantId, String workspaceId, String templateId, EmailStudioDto.DynamicRuleRequest request) {
        DynamicContentRule rule = new DynamicContentRule();
        rule.setTenantId(tenantId);
        rule.setWorkspaceId(workspaceId);
        rule.setTemplateId(templateId);
        apply(rule, request);
        return ruleRepository.save(rule);
    }

    @Transactional
    public DynamicContentRule update(String tenantId, String id, EmailStudioDto.DynamicRuleRequest request) {
        return update(tenantId, TenantContext.requireWorkspaceId(), id, request);
    }

    @Transactional
    public DynamicContentRule update(String tenantId, String workspaceId, String id, EmailStudioDto.DynamicRuleRequest request) {
        DynamicContentRule rule = get(tenantId, workspaceId, id);
        apply(rule, request);
        return ruleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public DynamicContentRule get(String tenantId, String id) {
        return get(tenantId, TenantContext.requireWorkspaceId(), id);
    }

    @Transactional(readOnly = true)
    public DynamicContentRule get(String tenantId, String workspaceId, String id) {
        return ruleRepository.findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(id, tenantId, workspaceId)
                .orElseThrow(() -> new NotFoundException("DynamicContentRule", id));
    }

    @Transactional(readOnly = true)
    public List<DynamicContentRule> list(String tenantId, String templateId) {
        return list(tenantId, TenantContext.requireWorkspaceId(), templateId);
    }

    @Transactional(readOnly = true)
    public List<DynamicContentRule> list(String tenantId, String workspaceId, String templateId) {
        return ruleRepository.findByTenantIdAndWorkspaceIdAndTemplateIdAndDeletedAtIsNullOrderByPriorityAsc(tenantId, workspaceId, templateId);
    }

    @Transactional
    public void delete(String tenantId, String id) {
        delete(tenantId, TenantContext.requireWorkspaceId(), id);
    }

    @Transactional
    public void delete(String tenantId, String workspaceId, String id) {
        DynamicContentRule rule = get(tenantId, workspaceId, id);
        rule.setDeletedAt(Instant.now());
        ruleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public DynamicRuleRenderResult resolveSlot(String tenantId, String templateId, String slotKey, Map<String, Object> variables) {
        return resolveSlot(tenantId, TenantContext.requireWorkspaceId(), templateId, slotKey, variables);
    }

    @Transactional(readOnly = true)
    public DynamicRuleRenderResult resolveSlot(String tenantId, String workspaceId, String templateId, String slotKey, Map<String, Object> variables) {
        List<DynamicContentRule> rules = ruleRepository
                .findByTenantIdAndWorkspaceIdAndTemplateIdAndSlotKeyAndActiveTrueAndDeletedAtIsNullOrderByPriorityAsc(tenantId, workspaceId, templateId, slotKey);
        for (DynamicContentRule rule : rules) {
            if (matches(rule, variables)) {
                return new DynamicRuleRenderResult(rule.getHtmlContent() == null ? "" : rule.getHtmlContent(),
                        rule.getTextContent() == null ? "" : rule.getTextContent(),
                        rule.getName(),
                        List.of());
            }
        }
        return new DynamicRuleRenderResult("", "", null, List.of("No dynamic content rule matched slot: " + slotKey));
    }

    public boolean matches(DynamicContentRule rule, Map<String, Object> variables) {
        String operator = normalizeOperator(rule.getOperator());
        if ("ALWAYS".equals(operator)) {
            return true;
        }
        Object actual = lookupValue(variables, rule.getConditionField());
        String actualValue = actual == null ? null : String.valueOf(actual);
        String expectedValue = rule.getConditionValue();
        return switch (operator) {
            case "EQUALS" -> expectedValue != null && expectedValue.equals(actualValue);
            case "NOT_EQUALS" -> expectedValue == null ? actualValue != null : !expectedValue.equals(actualValue);
            case "CONTAINS" -> actualValue != null && expectedValue != null && actualValue.contains(expectedValue);
            case "IN" -> actualValue != null && expectedValue != null
                    && Arrays.stream(expectedValue.split(",")).map(String::trim).anyMatch(actualValue::equals);
            case "EXISTS" -> actualValue != null && !actualValue.isBlank();
            default -> throw new ValidationException("operator", "Unsupported dynamic content operator: " + operator);
        };
    }

    private void apply(DynamicContentRule rule, EmailStudioDto.DynamicRuleRequest request) {
        if (request.getSlotKey() != null) rule.setSlotKey(request.getSlotKey().trim());
        if (request.getName() != null) rule.setName(request.getName().trim());
        if (request.getPriority() != null) rule.setPriority(request.getPriority());
        if (request.getConditionField() != null) rule.setConditionField(blankToNull(request.getConditionField()));
        if (request.getOperator() != null) rule.setOperator(normalizeOperator(request.getOperator()));
        if (request.getConditionValue() != null) rule.setConditionValue(request.getConditionValue());
        if (request.getHtmlContent() != null) rule.setHtmlContent(request.getHtmlContent());
        if (request.getTextContent() != null) rule.setTextContent(request.getTextContent());
        if (request.getActive() != null) rule.setActive(request.getActive());
        validateRule(rule);
    }

    private void validateRule(DynamicContentRule rule) {
        String operator = normalizeOperator(rule.getOperator());
        if (!List.of("ALWAYS", "EQUALS", "NOT_EQUALS", "CONTAINS", "IN", "EXISTS").contains(operator)) {
            throw new ValidationException("operator", "Unsupported dynamic content operator: " + operator);
        }
        if (!"ALWAYS".equals(operator) && (rule.getConditionField() == null || rule.getConditionField().isBlank())) {
            throw new ValidationException("conditionField", "Condition field is required for operator " + operator);
        }
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "ALWAYS" : operator.trim().toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private Object lookupValue(Map<String, Object> variables, String path) {
        if (variables == null || variables.isEmpty() || path == null || path.isBlank()) {
            return null;
        }
        Object current = variables;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record DynamicRuleRenderResult(String htmlContent, String textContent, String matchedRuleName, List<String> warnings) {}
}
