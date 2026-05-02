package com.legent.foundation.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.legent.foundation.domain.FeatureFlag;
import com.legent.security.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Feature flag rules evaluation engine.
 * Supports conditional targeting based on user attributes, time, tenant properties, etc.
 */
@Slf4j
@Component
public class FeatureFlagEngine {

    /**
     * Evaluates a feature flag with context-aware rules.
     * Returns true if the flag should be enabled for the current context.
     */
    public boolean evaluateWithRules(FeatureFlag flag, Map<String, Object> context) {
        // Null safety: Handle null flag
        if (flag == null) {
            log.debug("Null flag provided, returning false");
            return false;
        }
        
        // If flag is globally disabled, rules don't matter
        if (!flag.isEnabled()) {
            return false;
        }

        List<Map<String, Object>> rules = flag.getRules();
        if (rules == null || rules.isEmpty()) {
            // No rules = simple on/off flag
            return true;
        }

        // Evaluate rules - all rules must pass (AND logic)
        for (Map<String, Object> rule : rules) {
            if (!evaluateRule(rule, context)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Evaluates a single rule against the context.
     */
    private boolean evaluateRule(Map<String, Object> rule, Map<String, Object> context) {
        // Null safety: Handle null rule or context
        if (rule == null) {
            return true; // Null rule passes
        }
        
        String type = (String) rule.get("type");
        if (type == null) {
            return true; // No type = always pass
        }

        try {
            return switch (type.toUpperCase()) {
                case "PERCENTAGE_ROLLOUT" -> evaluatePercentageRollout(rule, context);
                case "USER_ATTRIBUTE" -> evaluateUserAttribute(rule, context);
                case "TIME_BASED" -> evaluateTimeBased(rule);
                case "DATE_RANGE" -> evaluateDateRange(rule);
                case "DAY_OF_WEEK" -> evaluateDayOfWeek(rule);
                case "TENANT_ATTRIBUTE" -> evaluateTenantAttribute(rule, context);
                case "IP_RANGE" -> evaluateIpRange(rule, context);
                case "ALWAYS" -> true;
                case "NEVER" -> false;
                default -> {
                    log.warn("Unknown rule type: {}", type);
                    yield true; // Unknown rules pass by default
                }
            };
        } catch (Exception e) {
            log.error("Error evaluating rule {}: {}", type, e.getMessage());
            return true; // Fail open on rule evaluation errors
        }
    }

    /**
     * Percentage rollout: Enable for X% of users based on hash.
     */
    private boolean evaluatePercentageRollout(Map<String, Object> rule, Map<String, Object> context) {
        if (rule == null) {
            return true;
        }
        Integer percentage = (Integer) rule.get("percentage");
        if (percentage == null || percentage >= 100) {
            return true;
        }
        if (percentage <= 0) {
            return false;
        }

        // Get user identifier for consistent hashing
        String userId = getUserIdentifier(context);
        if (userId == null) {
            return false; // Can't evaluate without user identifier
        }
        int hash = Math.abs(userId.hashCode()) % 100;
        return hash < percentage;
    }

    /**
     * User attribute match: Check if user attribute matches expected value.
     */
    private boolean evaluateUserAttribute(Map<String, Object> rule, Map<String, Object> context) {
        String attribute = (String) rule.get("attribute");
        Object expectedValue = rule.get("value");
        String operator = (String) rule.getOrDefault("operator", "EQUALS");

        if (attribute == null) {
            return true;
        }

        Object actualValue = context.get("user_" + attribute);
        if (actualValue == null) {
            actualValue = context.get(attribute);
        }

        return compareValues(actualValue, expectedValue, operator);
    }

    /**
     * Time-based rule: Enable only during specific hours.
     */
    private boolean evaluateTimeBased(Map<String, Object> rule) {
        Integer startHour = (Integer) rule.get("startHour");
        Integer endHour = (Integer) rule.get("endHour");
        String timezone = (String) rule.getOrDefault("timezone", "UTC");

        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        int currentHour = now.getHour();

        if (startHour != null && currentHour < startHour) {
            return false;
        }
        if (endHour != null && currentHour >= endHour) {
            return false;
        }

        return true;
    }

    /**
     * Date range rule: Enable only within specific dates.
     */
    private boolean evaluateDateRange(Map<String, Object> rule) {
        String startDate = (String) rule.get("startDate");
        String endDate = (String) rule.get("endDate");

        LocalDateTime now = LocalDateTime.now();

        if (startDate != null) {
            LocalDateTime start = LocalDateTime.parse(startDate);
            if (now.isBefore(start)) {
                return false;
            }
        }

        if (endDate != null) {
            LocalDateTime end = LocalDateTime.parse(endDate);
            if (now.isAfter(end)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Day of week rule: Enable only on specific days.
     */
    @SuppressWarnings("unchecked")
    private boolean evaluateDayOfWeek(Map<String, Object> rule) {
        List<String> allowedDays = (List<String>) rule.get("days");
        if (allowedDays == null || allowedDays.isEmpty()) {
            return true;
        }

        DayOfWeek today = LocalDateTime.now().getDayOfWeek();
        return allowedDays.contains(today.name());
    }

    /**
     * Tenant attribute match: Check tenant properties.
     */
    private boolean evaluateTenantAttribute(Map<String, Object> rule, Map<String, Object> context) {
        String attribute = (String) rule.get("attribute");
        Object expectedValue = rule.get("value");
        String operator = (String) rule.getOrDefault("operator", "EQUALS");

        if (attribute == null) {
            return true;
        }

        // Get from context or from TenantContext
        Object actualValue = context.get("tenant_" + attribute);
        if (actualValue == null) {
            actualValue = context.get(attribute);
        }
        if (actualValue == null && "plan".equals(attribute)) {
            // Could fetch from tenant service if needed
            actualValue = context.get("tenantPlan");
        }

        return compareValues(actualValue, expectedValue, operator);
    }

    /**
     * IP range rule: Enable for specific IP ranges.
     */
    @SuppressWarnings("unchecked")
    private boolean evaluateIpRange(Map<String, Object> rule, Map<String, Object> context) {
        String userIp = (String) context.get("ip_address");
        if (userIp == null) {
            return true; // Can't evaluate, fail open
        }

        List<String> allowedIps = (List<String>) rule.get("allowedIps");
        List<String> blockedIps = (List<String>) rule.get("blockedIps");

        // Check blocked first
        if (blockedIps != null && blockedIps.contains(userIp)) {
            return false;
        }

        // Check allowed (if specified)
        if (allowedIps != null && !allowedIps.isEmpty()) {
            return allowedIps.contains(userIp);
        }

        return true;
    }

    /**
     * Compare two values with various operators.
     */
    private boolean compareValues(Object actual, Object expected, String operator) {
        if (actual == null && expected == null) {
            return operator.equals("EQUALS") || operator.equals("IS_NULL");
        }
        if (actual == null) {
            return operator.equals("IS_NULL");
        }

        return switch (operator.toUpperCase()) {
            case "EQUALS", "==" -> actual.equals(expected);
            case "NOT_EQUALS", "!=" -> !actual.equals(expected);
            case "CONTAINS" -> actual.toString().contains(expected != null ? expected.toString() : "");
            case "STARTS_WITH" -> actual.toString().startsWith(expected != null ? expected.toString() : "");
            case "ENDS_WITH" -> actual.toString().endsWith(expected != null ? expected.toString() : "");
            case "IN" -> {
                if (expected instanceof List<?> list) {
                    yield list.contains(actual);
                }
                yield false;
            }
            case "NOT_IN" -> {
                if (expected instanceof List<?> list) {
                    yield !list.contains(actual);
                }
                yield true;
            }
            case "GREATER_THAN", ">" -> compareNumeric(actual, expected) > 0;
            case "LESS_THAN", "<" -> compareNumeric(actual, expected) < 0;
            case "GREATER_OR_EQUAL", ">=" -> compareNumeric(actual, expected) >= 0;
            case "LESS_OR_EQUAL", "<=" -> compareNumeric(actual, expected) <= 0;
            default -> actual.equals(expected);
        };
    }

    /**
     * Compare two numeric values.
     */
    private int compareNumeric(Object a, Object b) {
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return a.toString().compareTo(b != null ? b.toString() : "");
    }

    /**
     * Get a consistent user identifier from context.
     */
    private String getUserIdentifier(Map<String, Object> context) {
        if (context == null) {
            // Fallback to TenantContext
            String userId = TenantContext.getUserId();
            if (userId != null) return userId;
            String tenantId = TenantContext.getTenantId();
            return tenantId != null ? tenantId : "anonymous";
        }
        
        // Try various identifiers in order of preference
        String userId = (String) context.get("user_id");
        if (userId != null) return userId;

        userId = (String) context.get("subscriber_id");
        if (userId != null) return userId;

        userId = TenantContext.getUserId();
        if (userId != null) return userId;

        String tenantId = TenantContext.getTenantId();
        if (tenantId != null) return tenantId;

        // Fallback to session-based identifier
        return context.hashCode() + "_" + System.currentTimeMillis();
    }

    /**
     * Build evaluation context from available data.
     */
    public Map<String, Object> buildContext(String userId, String tenantId, String ipAddress,
                                             Map<String, Object> userAttributes) {
        Map<String, Object> context = new java.util.HashMap<>();

        if (userId != null) {
            context.put("user_id", userId);
        }
        if (tenantId != null) {
            context.put("tenant_id", tenantId);
        }
        if (ipAddress != null) {
            context.put("ip_address", ipAddress);
        }
        if (userAttributes != null) {
            context.putAll(userAttributes);
        }

        // Add current time info
        LocalDateTime now = LocalDateTime.now();
        context.put("current_hour", now.getHour());
        context.put("current_day", now.getDayOfWeek().name());
        context.put("current_date", now.toLocalDate().toString());

        return context;
    }
}
