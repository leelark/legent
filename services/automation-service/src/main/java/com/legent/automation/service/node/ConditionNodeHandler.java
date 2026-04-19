package com.legent.automation.service.node;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.legent.automation.domain.WorkflowInstance;
import com.legent.automation.dto.WorkflowGraphDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionNodeHandler implements NodeHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "CONDITION";
    }

    @Override
    public String execute(WorkflowInstance instance, WorkflowGraphDto.WorkflowNode node) {
        Map<String, Object> context = parseContext(instance.getContext());
        List<WorkflowGraphDto.ConditionEdge> branches = node.getBranches();

        if (branches == null || branches.isEmpty()) {
            return node.getNextNodeId();
        }

        for (WorkflowGraphDto.ConditionEdge branch : branches) {
            if (matchesBranch(branch.getCondition(), context)) {
                return branch.getTargetNodeId();
            }
        }

        return node.getNextNodeId();
    }

    private Map<String, Object> parseContext(String rawContext) {
        if (rawContext == null || rawContext.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(rawContext, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse instance context for condition evaluation", e);
            throw new RuntimeException("Context mapping error", e);
        }
    }

    private boolean matchesBranch(String condition, Map<String, Object> context) {
        if (condition == null || condition.isBlank()) {
            return false;
        }

        String trimmed = condition.trim();

        // Backward-compatible support for the initial "true_path"/"false_path" branch style.
        if ("true_path".equalsIgnoreCase(trimmed) || "false_path".equalsIgnoreCase(trimmed)) {
            boolean hasOpened = asBoolean(context.get("hasOpened"));
            return "true_path".equalsIgnoreCase(trimmed) ? hasOpened : !hasOpened;
        }

        String[] operators = {"==", "!=", ">=", "<=", ">", "<"};
        for (String operator : operators) {
            int operatorIndex = trimmed.indexOf(operator);
            if (operatorIndex > 0) {
                String leftExpr = trimmed.substring(0, operatorIndex).trim();
                String rightExpr = trimmed.substring(operatorIndex + operator.length()).trim();

                Object leftValue = resolveValue(leftExpr, context);
                Object rightValue = resolveValue(rightExpr, context);
                return compare(leftValue, rightValue, operator);
            }
        }

        if (trimmed.startsWith("!")) {
            Object value = resolveValue(trimmed.substring(1).trim(), context);
            return !asBoolean(value);
        }

        return asBoolean(resolveValue(trimmed, context));
    }

    private Object resolveValue(String token, Map<String, Object> context) {
        if (token == null || token.isBlank()) {
            return null;
        }

        if ((token.startsWith("'") && token.endsWith("'")) || (token.startsWith("\"") && token.endsWith("\""))) {
            return token.substring(1, token.length() - 1);
        }

        if ("true".equalsIgnoreCase(token) || "false".equalsIgnoreCase(token)) {
            return Boolean.parseBoolean(token);
        }

        if ("null".equalsIgnoreCase(token)) {
            return null;
        }

        if (isNumberToken(token)) {
            return new BigDecimal(token);
        }

        if (context.containsKey(token)) {
            return context.get(token);
        }

        return token;
    }

    private boolean compare(Object left, Object right, String operator) {
        if (left instanceof Number || right instanceof Number) {
            BigDecimal leftNumber = toBigDecimal(left);
            BigDecimal rightNumber = toBigDecimal(right);
            if (leftNumber == null || rightNumber == null) {
                return false;
            }

            int comparison = leftNumber.compareTo(rightNumber);
            return switch (operator) {
                case "==" -> comparison == 0;
                case "!=" -> comparison != 0;
                case ">" -> comparison > 0;
                case "<" -> comparison < 0;
                case ">=" -> comparison >= 0;
                case "<=" -> comparison <= 0;
                default -> false;
            };
        }

        if ("==".equals(operator)) {
            return java.util.Objects.equals(left, right);
        }
        if ("!=".equals(operator)) {
            return !java.util.Objects.equals(left, right);
        }

        String leftString = left == null ? "" : String.valueOf(left);
        String rightString = right == null ? "" : String.valueOf(right);
        int comparison = leftString.compareTo(rightString);

        return switch (operator) {
            case ">" -> comparison > 0;
            case "<" -> comparison < 0;
            case ">=" -> comparison >= 0;
            case "<=" -> comparison <= 0;
            default -> false;
        };
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue() != 0D;
        }
        if (value instanceof String stringValue) {
            String normalized = stringValue.trim().toLowerCase(Locale.ROOT);
            return !(normalized.isEmpty() || "false".equals(normalized) || "0".equals(normalized) || "null".equals(normalized));
        }
        return value != null;
    }

    private boolean isNumberToken(String value) {
        return value.matches("-?\\d+(\\.\\d+)?");
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        if (value instanceof String string && isNumberToken(string.trim())) {
            return new BigDecimal(string.trim());
        }
        return null;
    }
}
