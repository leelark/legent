package com.legent.audience.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SegmentRuleExecutionPlanCompiler {

    static final int MAX_GROUP_DEPTH = 8;
    static final String EXECUTION_MODE = "BOUNDED_SQL";

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
    private static final Set<String> NULL_OPERATORS = Set.of("IS_NULL", "IS_NOT_NULL");
    private static final Set<String> WILDCARD_SCAN_OPERATORS = Set.of("CONTAINS", "ENDS_WITH");

    private SegmentRuleExecutionPlanCompiler() {
    }

    static CompiledPlan compile(Map<String, Object> rules) {
        CompileContext context = new CompileContext();
        String whereClause = compileRuleGroup(rules, "rules", 0, context);
        return new CompiledPlan(
                whereClause,
                Map.copyOf(context.parameters),
                List.copyOf(context.steps),
                List.copyOf(context.requiredIndexes),
                List.copyOf(context.warnings),
                context.conditionCount,
                context.maxDepth,
                true);
    }

    @SuppressWarnings("unchecked")
    private static String compileRuleGroup(Map<?, ?> group, String path, int depth, CompileContext context) {
        if (group == null) {
            throw new IllegalArgumentException(path + " is required");
        }
        if (depth > MAX_GROUP_DEPTH) {
            throw new IllegalArgumentException(path + " exceeds maximum segment rule depth of " + MAX_GROUP_DEPTH);
        }
        context.maxDepth = Math.max(context.maxDepth, depth);

        String operator = stringValueOrDefault(group.get("operator"), "AND", path + ".operator")
                .toUpperCase(Locale.ROOT);
        if (!"AND".equals(operator) && !"OR".equals(operator)) {
            throw new IllegalArgumentException(path + ".operator must be AND or OR");
        }

        List<String> clauses = new ArrayList<>();
        List<?> conditions = optionalList(group.get("conditions"), path + ".conditions");
        for (int i = 0; i < conditions.size(); i++) {
            Object condition = conditions.get(i);
            if (!(condition instanceof Map<?, ?> conditionMap)) {
                throw new IllegalArgumentException(path + ".conditions[" + i + "] must be an object");
            }
            clauses.add(compileCondition(conditionMap, path + ".conditions[" + i + "]", context));
        }

        List<?> groups = optionalList(group.get("groups"), path + ".groups");
        for (int i = 0; i < groups.size(); i++) {
            Object child = groups.get(i);
            if (!(child instanceof Map<?, ?> childMap)) {
                throw new IllegalArgumentException(path + ".groups[" + i + "] must be an object");
            }
            String childClause = compileRuleGroup(childMap, path + ".groups[" + i + "]", depth + 1, context);
            if (!childClause.isBlank()) {
                clauses.add("(" + childClause + ")");
            }
        }

        return String.join(" " + operator + " ", clauses);
    }

    private static String compileCondition(Map<?, ?> condition, String path, CompileContext context) {
        String field = requiredString(condition.get("field"), path + ".field");
        String operator = requiredString(condition.get("op"), path + ".op").toUpperCase(Locale.ROOT);
        validateFieldName(field, path + ".field");
        validateNoUnsupportedDataExtensionField(condition, field, path);
        if (!SUPPORTED_CONDITION_OPERATORS.contains(operator)) {
            throw new IllegalArgumentException("Unsupported segment operator: " + operator);
        }

        boolean listMembershipField = "list_membership".equalsIgnoreCase(field);
        if (LIST_MEMBERSHIP_OPERATORS.contains(operator)) {
            if (!listMembershipField) {
                throw new IllegalArgumentException("List membership operators require list_membership field");
            }
            String listId = requiredString(condition.get("value"), path + ".value");
            String paramName = context.nextParameterName(listId);
            context.conditionCount++;
            context.requiredIndexes.add("idx_list_memberships_candidate_active");
            context.steps.add(new PlanStep(
                    path,
                    "LIST_MEMBERSHIP",
                    "list_membership",
                    operator,
                    "IN_LIST".equals(operator) ? "SCOPED_EXISTS" : "SCOPED_NOT_EXISTS",
                    true,
                    true));
            if ("IN_LIST".equals(operator)) {
                return "EXISTS (SELECT 1 FROM list_memberships lm WHERE lm.tenant_id = :tid AND lm.workspace_id = :wid AND lm.subscriber_id = s.id AND lm.list_id = :"
                        + paramName + " AND lm.status = 'ACTIVE')";
            }
            return "NOT EXISTS (SELECT 1 FROM list_memberships lm WHERE lm.tenant_id = :tid AND lm.workspace_id = :wid AND lm.subscriber_id = s.id AND lm.list_id = :"
                    + paramName + " AND lm.status = 'ACTIVE')";
        }

        if (listMembershipField) {
            throw new IllegalArgumentException("list_membership only supports IN_LIST and NOT_IN_LIST operators");
        }

        if ("IN_SEGMENT".equals(operator)) {
            String segmentId = requiredString(condition.get("value"), path + ".value");
            String paramName = context.nextParameterName(segmentId);
            context.conditionCount++;
            context.requiredIndexes.add("idx_segment_memberships_candidate");
            context.steps.add(new PlanStep(
                    path,
                    "SEGMENT_MEMBERSHIP",
                    field,
                    operator,
                    "SCOPED_EXISTS",
                    true,
                    true));
            return "EXISTS (SELECT 1 FROM segment_memberships sm WHERE sm.tenant_id = :tid AND sm.workspace_id = :wid AND sm.subscriber_id = s.id AND sm.segment_id = :"
                    + paramName + ")";
        }

        String column = mapFieldToColumn(field);
        context.conditionCount++;
        if (column.startsWith("s.custom_fields")) {
            context.requiredIndexes.add("idx_sub_custom_fields");
            context.warnings.add("CUSTOM_FIELD_FILTER_REQUIRES_INDEX_REVIEW");
        }
        if (WILDCARD_SCAN_OPERATORS.contains(operator)) {
            context.warnings.add("WILDCARD_TEXT_FILTER_REQUIRES_CARDINALITY_REVIEW");
        }
        context.steps.add(new PlanStep(
                path,
                column.startsWith("s.custom_fields") ? "CUSTOM_FIELD" : "SUBSCRIBER_FIELD",
                field,
                operator,
                "SUBSCRIBER_FILTER",
                true,
                false));

        if (NULL_OPERATORS.contains(operator)) {
            return "IS_NULL".equals(operator) ? column + " IS NULL" : column + " IS NOT NULL";
        }

        Object value = requirePresentValue(condition.get("value"), path + ".value");
        String paramName = switch (operator) {
            case "CONTAINS" -> context.nextParameterName("%" + value + "%");
            case "STARTS_WITH" -> context.nextParameterName(value + "%");
            case "ENDS_WITH" -> context.nextParameterName("%" + value);
            default -> context.nextParameterName(value);
        };

        return switch (operator) {
            case "EQUALS" -> column + " = :" + paramName;
            case "NOT_EQUALS" -> column + " != :" + paramName;
            case "CONTAINS", "STARTS_WITH", "ENDS_WITH" -> column + " ILIKE :" + paramName;
            case "GREATER_THAN" -> column + " > :" + paramName;
            case "LESS_THAN" -> column + " < :" + paramName;
            default -> throw new IllegalArgumentException("Unsupported segment operator: " + operator);
        };
    }

    private static List<?> optionalList(Object value, String fieldName) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(fieldName + " must be an array");
        }
        return list;
    }

    private static Object requirePresentValue(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value instanceof String text && text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static String requiredString(Object value, String fieldName) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return text.trim();
    }

    private static String stringValueOrDefault(Object value, String defaultValue, String fieldName) {
        if (value == null) {
            return defaultValue;
        }
        return requiredString(value, fieldName);
    }

    private static void validateFieldName(String field, String fieldName) {
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

    private static void validateNoUnsupportedDataExtensionField(Map<?, ?> condition, String field, String path) {
        if (condition.containsKey("dataExtensionId")
                || condition.containsKey("relationship")
                || condition.containsKey("relationshipName")
                || condition.containsKey("relationshipPath")) {
            throw new IllegalArgumentException(path + ".field uses data extension relationships, which are not supported by segment rules yet");
        }
        String normalized = field.toLowerCase(Locale.ROOT);
        if ("data_extension".equals(normalized)
                || "data_extension_field".equals(normalized)
                || "relationship_path".equals(normalized)) {
            throw new IllegalArgumentException(path + ".field uses data extension relationships, which are not supported by segment rules yet");
        }
    }

    private static String mapFieldToColumn(String field) {
        String lowerField = field.toLowerCase(Locale.ROOT);
        return switch (lowerField) {
            case "email" -> "s.email";
            case "first_name", "firstname" -> "s.first_name";
            case "last_name", "lastname" -> "s.last_name";
            case "status" -> "s.status";
            case "source" -> "s.source";
            case "locale" -> "s.locale";
            case "timezone" -> "s.timezone";
            case "phone" -> "s.phone";
            case "subscriber_key", "subscriberkey" -> "s.subscriber_key";
            case "created_at", "createdat" -> "s.created_at";
            case "subscribed_at", "subscribedat" -> "s.subscribed_at";
            case "last_activity_at", "lastactivityat" -> "s.last_activity_at";
            default -> "s.custom_fields #>> '{" + lowerField + "}'";
        };
    }

    private static boolean isSqlKeyword(String field) {
        return switch (field) {
            case "select", "insert", "update", "delete", "drop", "create", "alter",
                 "where", "and", "or", "not", "null", "true", "false",
                 "union", "join", "from", "table", "column", "database",
                 "exec", "execute", "script", "eval", "cast", "convert" -> true;
            default -> false;
        };
    }

    record CompiledPlan(
            String whereClause,
            Map<String, Object> parameters,
            List<PlanStep> steps,
            List<String> requiredIndexes,
            List<String> warnings,
            int conditionCount,
            int maxDepth,
            boolean bounded) {
    }

    record PlanStep(
            String path,
            String family,
            String field,
            String operator,
            String strategy,
            boolean tenantWorkspaceScoped,
            boolean indexedLookup) {
    }

    private static final class CompileContext {
        private final LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        private final List<PlanStep> steps = new ArrayList<>();
        private final LinkedHashSet<String> requiredIndexes = new LinkedHashSet<>();
        private final LinkedHashSet<String> warnings = new LinkedHashSet<>();
        private int nextParameterIndex;
        private int conditionCount;
        private int maxDepth;

        private CompileContext() {
            requiredIndexes.add("idx_subscribers_candidate_keyset");
        }

        private String nextParameterName(Object value) {
            String name = "p" + nextParameterIndex++;
            parameters.put(name, value);
            return name;
        }
    }
}
