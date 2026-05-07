package com.legent.content.service;

import com.legent.content.domain.DynamicContentRule;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicContentRuleServiceTest {

    private final DynamicContentRuleService service = new DynamicContentRuleService(null);

    @Test
    void matchesEqualsAndNestedFields() {
        DynamicContentRule rule = new DynamicContentRule();
        rule.setOperator("EQUALS");
        rule.setConditionField("subscriber.segment");
        rule.setConditionValue("vip");

        assertTrue(service.matches(rule, Map.of("subscriber", Map.of("segment", "vip"))));
        assertFalse(service.matches(rule, Map.of("subscriber", Map.of("segment", "standard"))));
    }

    @Test
    void matchesInOperator() {
        DynamicContentRule rule = new DynamicContentRule();
        rule.setOperator("IN");
        rule.setConditionField("country");
        rule.setConditionValue("US,CA,IN");

        assertTrue(service.matches(rule, Map.of("country", "IN")));
        assertFalse(service.matches(rule, Map.of("country", "GB")));
    }
}
