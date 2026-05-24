package com.legent.audience.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SegmentRuleExecutionPlanCompiler Unit Tests")
class SegmentRuleExecutionPlanCompilerTest {

    @Test
    @DisplayName("compile builds a bounded scoped plan for existing rule families")
    void compile_buildsBoundedScopedPlanForExistingRuleFamilies() {
        SegmentRuleExecutionPlanCompiler.CompiledPlan plan = SegmentRuleExecutionPlanCompiler.compile(
                Map.of(
                        "operator", "AND",
                        "conditions", List.of(
                                Map.of("field", "status", "op", "EQUALS", "value", "ACTIVE"),
                                Map.of("field", "list_membership", "op", "IN_LIST", "value", "list-1")),
                        "groups", List.of(Map.of(
                                "operator", "OR",
                                "conditions", List.of(
                                        Map.of("field", "loyalty_tier", "op", "CONTAINS", "value", "gold"),
                                        Map.of("field", "segment_ref", "op", "IN_SEGMENT", "value", "seg-2"))))));

        assertThat(plan.bounded()).isTrue();
        assertThat(plan.conditionCount()).isEqualTo(4);
        assertThat(plan.maxDepth()).isEqualTo(1);
        assertThat(plan.whereClause())
                .contains("s.status = :p0")
                .contains("EXISTS (SELECT 1 FROM list_memberships lm")
                .contains("s.custom_fields #>> '{loyalty_tier}' ILIKE :p2")
                .contains("EXISTS (SELECT 1 FROM segment_memberships sm");
        assertThat(plan.parameters())
                .containsEntry("p0", "ACTIVE")
                .containsEntry("p1", "list-1")
                .containsEntry("p2", "%gold%")
                .containsEntry("p3", "seg-2");
        assertThat(plan.requiredIndexes())
                .contains(
                        "idx_subscribers_candidate_keyset",
                        "idx_list_memberships_candidate_active",
                        "idx_sub_custom_fields",
                        "idx_segment_memberships_candidate");
        assertThat(plan.steps())
                .extracting(SegmentRuleExecutionPlanCompiler.PlanStep::family)
                .containsExactly("SUBSCRIBER_FIELD", "LIST_MEMBERSHIP", "CUSTOM_FIELD", "SEGMENT_MEMBERSHIP");
        assertThat(plan.steps())
                .allMatch(SegmentRuleExecutionPlanCompiler.PlanStep::tenantWorkspaceScoped);
    }

    @Test
    @DisplayName("compile rejects unsupported data extension relationship traversal")
    void compile_rejectsRelationshipTraversal() {
        Map<String, Object> rules = Map.of("operator", "AND", "conditions", List.of(Map.of(
                "field", "email",
                "op", "EQUALS",
                "value", "person@example.com",
                "relationshipPath", "profile.email")));

        assertThatThrownBy(() -> SegmentRuleExecutionPlanCompiler.compile(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data extension relationships");
    }

    @Test
    @DisplayName("compile rejects generic operators without explicit values")
    void compile_rejectsMissingValueForComparisonOperators() {
        Map<String, Object> rules = Map.of("operator", "AND", "conditions", List.of(Map.of(
                "field", "status",
                "op", "EQUALS")));

        assertThatThrownBy(() -> SegmentRuleExecutionPlanCompiler.compile(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rules.conditions[0].value is required");
    }

    @Test
    @DisplayName("compile rejects unsupported nested group depth")
    void compile_rejectsUnsupportedNestedDepth() {
        Map<String, Object> rules = emptyGroup();
        Map<String, Object> current = rules;
        for (int i = 0; i <= SegmentRuleExecutionPlanCompiler.MAX_GROUP_DEPTH; i++) {
            Map<String, Object> child = emptyGroup();
            current.put("groups", List.of(child));
            current = child;
        }

        assertThatThrownBy(() -> SegmentRuleExecutionPlanCompiler.compile(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum segment rule depth");
    }

    private Map<String, Object> emptyGroup() {
        return new java.util.LinkedHashMap<>(Map.of(
                "operator", "AND",
                "conditions", List.of()));
    }
}
