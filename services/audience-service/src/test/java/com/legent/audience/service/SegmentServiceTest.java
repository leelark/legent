package com.legent.audience.service;

import com.legent.audience.domain.Segment;
import com.legent.audience.dto.SegmentDto;
import com.legent.audience.event.SegmentEventPublisher;
import com.legent.audience.mapper.SegmentMapper;
import com.legent.audience.repository.SegmentRepository;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SegmentService Unit Tests")
class SegmentServiceTest {

    private static final String TENANT_ID = "tenant-001";
    private static final String WORKSPACE_ID = "ws-001";

    @Mock private SegmentRepository segmentRepository;
    @Mock private SegmentMapper segmentMapper;
    @Mock private SegmentEventPublisher eventPublisher;
    @InjectMocks private SegmentService segmentService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("create rejects unsupported condition operator")
    void create_rejectsUnsupportedConditionOperator() {
        SegmentDto.CreateRequest request = createRequest(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "email", "op", "MATCHES_REGEX", "value", ".*@example.com")
        )));

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "High value"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported segment operator");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("update rejects unsupported condition operator")
    void update_rejectsUnsupportedConditionOperator() {
        Segment existing = new Segment();
        existing.setId("seg-1");
        existing.setTenantId(TENANT_ID);
        existing.setWorkspaceId(WORKSPACE_ID);
        existing.setName("High value");
        existing.setRules(validEmailRule());

        SegmentDto.UpdateRequest request = SegmentDto.UpdateRequest.builder()
                .rules(Map.of("operator", "AND", "conditions", List.of(
                        Map.of("field", "email", "op", "MATCHES_REGEX", "value", ".*@example.com")
                )))
                .build();

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-1"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> segmentService.update("seg-1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported segment operator");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishUpdated(any());
    }

    @Test
    @DisplayName("create rejects list membership with unsupported operator")
    void create_rejectsListMembershipWithUnsupportedOperator() {
        SegmentDto.CreateRequest request = createRequest(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "list_membership", "op", "EQUALS", "value", "list-1")
        )));

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "High value"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("list_membership only supports");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("create rejects list operator on non-list field")
    void create_rejectsListOperatorOnNonListField() {
        SegmentDto.CreateRequest request = createRequest(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "email", "op", "IN_LIST", "value", "list-1")
        )));

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "High value"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List membership operators require");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("create rejects invalid field names")
    void create_rejectsInvalidFieldName() {
        SegmentDto.CreateRequest request = createRequest(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "email;drop", "op", "EQUALS", "value", "person@example.com")
        )));

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "High value"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains invalid characters");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("create rejects comparison rules without explicit values")
    void create_rejectsComparisonRuleWithoutValue() {
        SegmentDto.CreateRequest request = createRequest(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "email", "op", "EQUALS")
        )));

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "High value"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rules.conditions[0].value is required");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("create rejects unsupported nested rule depth")
    void create_rejectsUnsupportedNestedRuleDepth() {
        Map<String, Object> rules = emptyGroup();
        Map<String, Object> current = rules;
        for (int i = 0; i <= SegmentRuleExecutionPlanCompiler.MAX_GROUP_DEPTH; i++) {
            Map<String, Object> child = emptyGroup();
            current.put("groups", List.of(child));
            current = child;
        }
        SegmentDto.CreateRequest request = createRequest(rules);

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "High value"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum segment rule depth");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("create rejects unsupported data extension relationship fields")
    void create_rejectsUnsupportedDataExtensionRelationshipField() {
        SegmentDto.CreateRequest request = createRequest(Map.of("operator", "AND", "conditions", List.of(
                Map.of(
                        "field", "email",
                        "op", "EQUALS",
                        "value", "person@example.com",
                        "dataExtensionId", "de-1",
                        "relationshipPath", "profile.email")
        )));

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "High value"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data extension relationships");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("create accepts valid list membership rule")
    void create_acceptsValidListMembershipRule() {
        Map<String, Object> rules = Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "list_membership", "op", "IN_LIST", "value", "list-1")
        ));
        SegmentDto.CreateRequest request = createRequest(rules);
        Segment entity = segmentFrom(request);
        SegmentDto.Response response = SegmentDto.Response.builder()
                .id("seg-1")
                .name("High value")
                .rules(rules)
                .build();

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "High value"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(entity);
        when(segmentRepository.save(entity)).thenAnswer(invocation -> invocation.getArgument(0));
        when(segmentMapper.toResponse(entity)).thenReturn(response);

        SegmentDto.Response result = segmentService.create(request);

        assertThat(result).isSameAs(response);
        assertThat(entity.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(entity.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        verify(segmentRepository).save(entity);
        verify(eventPublisher).publishCreated(entity);
    }

    @Test
    @DisplayName("create rejects predictive segment without governance contract")
    void create_rejectsPredictiveSegmentWithoutGovernanceContract() {
        SegmentDto.CreateRequest request = SegmentDto.CreateRequest.builder()
                .name("Predicted buyers")
                .segmentType("PREDICTIVE")
                .rules(validEmailRule())
                .build();

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "Predicted buyers"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("predictiveGovernance");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("create rejects predictive segment with protected feature data")
    void create_rejectsPredictiveSegmentWithProtectedFeatureData() {
        Map<String, Object> rules = predictiveRules(validPredictiveGovernance());
        @SuppressWarnings("unchecked")
        Map<String, Object> governance = (Map<String, Object>) rules.get(PredictiveSegmentGovernanceService.GOVERNANCE_KEY);
        governance.put("dataClassesUsed", List.of("health_data"));

        SegmentDto.CreateRequest request = SegmentDto.CreateRequest.builder()
                .name("Predicted buyers")
                .segmentType("PREDICTIVE")
                .rules(rules)
                .build();

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "Predicted buyers"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PROHIBITED_DATA_CLASS:HEALTH_DATA");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("create rejects scheduled predictive segment")
    void create_rejectsScheduledPredictiveSegment() {
        SegmentDto.CreateRequest request = SegmentDto.CreateRequest.builder()
                .name("Predicted buyers")
                .segmentType("PREDICTIVE")
                .scheduleEnabled(true)
                .rules(predictiveRules(validPredictiveGovernance()))
                .build();

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "Predicted buyers"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(segmentFrom(request));

        assertThatThrownBy(() -> segmentService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheduled recompute");

        verify(segmentRepository, never()).save(any());
        verify(eventPublisher, never()).publishCreated(any());
    }

    @Test
    @DisplayName("create accepts approved predictive governance contract")
    void create_acceptsApprovedPredictiveGovernanceContract() {
        Map<String, Object> rules = predictiveRules(validPredictiveGovernance());
        SegmentDto.CreateRequest request = SegmentDto.CreateRequest.builder()
                .name("Predicted buyers")
                .segmentType("PREDICTIVE")
                .rules(rules)
                .build();
        Segment entity = segmentFrom(request);
        SegmentDto.Response response = SegmentDto.Response.builder()
                .id("seg-predictive")
                .name("Predicted buyers")
                .segmentType("PREDICTIVE")
                .rules(rules)
                .build();

        when(segmentRepository.existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "Predicted buyers"))
                .thenReturn(false);
        when(segmentMapper.toEntity(request)).thenReturn(entity);
        when(segmentRepository.save(entity)).thenAnswer(invocation -> invocation.getArgument(0));
        when(segmentMapper.toResponse(entity)).thenReturn(response);

        SegmentDto.Response result = segmentService.create(request);

        assertThat(result).isSameAs(response);
        assertThat(entity.getSegmentType()).isEqualTo(Segment.SegmentType.PREDICTIVE);
        assertThat(entity.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(entity.getWorkspaceId()).isEqualTo(WORKSPACE_ID);
        verify(segmentRepository).save(entity);
        verify(eventPublisher).publishCreated(entity);
    }

    private SegmentDto.CreateRequest createRequest(Map<String, Object> rules) {
        return SegmentDto.CreateRequest.builder()
                .name("High value")
                .segmentType("FILTER")
                .rules(rules)
                .build();
    }

    private Segment segmentFrom(SegmentDto.CreateRequest request) {
        Segment segment = new Segment();
        segment.setName(request.getName());
        segment.setRules(request.getRules());
        segment.setScheduleEnabled(request.isScheduleEnabled());
        return segment;
    }

    private Map<String, Object> validEmailRule() {
        return Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "email", "op", "EQUALS", "value", "person@example.com")
        ));
    }

    private Map<String, Object> emptyGroup() {
        return new LinkedHashMap<>(Map.of(
                "operator", "AND",
                "conditions", List.of()));
    }

    private Map<String, Object> predictiveRules(Map<String, Object> governance) {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("operator", "AND");
        rules.put("conditions", List.of(Map.of("field", "status", "op", "EQUALS", "value", "ACTIVE")));
        rules.put(PredictiveSegmentGovernanceService.GOVERNANCE_KEY, governance);
        return rules;
    }

    private Map<String, Object> validPredictiveGovernance() {
        Map<String, Object> governance = new LinkedHashMap<>();
        governance.put("tenantPolicyEnabled", true);
        governance.put("policyVersion", "ai-policy-v1");
        governance.put("derivationMode", "MODEL_BACKED");
        governance.put("featureSources", List.of("SUBSCRIBER_PROFILE", "ENGAGEMENT_EVENTS", "SUPPRESSION_STATUS"));
        governance.put("dataClassesUsed", List.of("SUBSCRIBER_PROFILE", "ENGAGEMENT_EVENTS", "SUPPRESSION_STATUS"));
        governance.put("excludedDataClasses", List.of("PROTECTED_CLASS", "HEALTH_DATA", "PAYMENT_DATA"));
        governance.put("protectedDataExcluded", true);
        governance.put("eligibleContactCount", 2_000L);
        governance.put("historicalEventCount", 4_000L);
        governance.put("modeledCount", 800L);
        governance.put("suppressionImpactCount", 100L);
        governance.put("dataFreshnessDays", 14);
        governance.put("biasDriftCheckPassed", true);
        governance.put("approvalStatus", "APPROVED");
        governance.put("approvedBy", "approver-1");
        governance.put("approvedAt", "2026-05-20T12:00:00Z");
        governance.put("rollbackSnapshotId", "snapshot-1");
        governance.put("reasonCodes", List.of("HIGH_ENGAGEMENT_PROPENSITY", "RECENT_ACTIVITY"));
        return governance;
    }
}
