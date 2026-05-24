package com.legent.audience.service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import com.legent.audience.domain.Segment;
import com.legent.audience.dto.SegmentDto;
import com.legent.audience.event.SegmentEventPublisher;
import com.legent.audience.repository.SegmentMembershipRepository;
import com.legent.audience.repository.SegmentRepository;
import com.legent.cache.service.CacheService;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SegmentEvaluationService Unit Tests")
class SegmentEvaluationServiceTest {

    @Mock private SegmentRepository segmentRepository;
    @Mock private SegmentMembershipRepository membershipRepository;
    @Mock private CacheService cacheService;
    @Mock private SegmentEventPublisher eventPublisher;
    @Mock private EntityManager entityManager;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ObjectProvider<SegmentEvaluationService> selfProvider;
    @InjectMocks private SegmentEvaluationService evaluationService;

    private static final String TENANT_ID = "tenant-001";
    private static final String WORKSPACE_ID = "ws-001";

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        TenantContext.setWorkspaceId(WORKSPACE_ID);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    @Test
    @DisplayName("evaluateCount returns cached result when available")
    void evaluateCount_cached() {
        SegmentDto.CountPreview cached = SegmentDto.CountPreview.builder()
                .segmentId("seg-1").count(42).evaluationMs(10).build();

        Segment segment = new Segment();
        segment.setTenantId(TENANT_ID);
        segment.setRules(Map.of());

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-1"))
                .thenReturn(Optional.of(segment));
        when(cacheService.get(anyString(), eq(SegmentDto.CountPreview.class)))
                .thenReturn(Optional.of(cached));

        SegmentDto.CountPreview result = evaluationService.evaluateCount("seg-1");

        assertThat(result.getCount()).isEqualTo(42);
        assertThat(result.getExecutionPlan()).isNotNull();
        assertThat(result.getExecutionPlan().isBounded()).isTrue();
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("evaluateCount runs query when no cache")
    void evaluateCount_noCache() {
        Segment segment = new Segment();
        segment.setTenantId(TENANT_ID);
        segment.setRules(Map.of("operator", "AND", "conditions", java.util.List.of(
                Map.of("field", "status", "op", "EQUALS", "value", "ACTIVE")
        )));

        Query mockQuery = mock(Query.class);
        when(mockQuery.getSingleResult()).thenReturn(100L);

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-2"))
                .thenReturn(Optional.of(segment));
        when(cacheService.get(anyString(), eq(SegmentDto.CountPreview.class)))
                .thenReturn(Optional.empty());
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

        SegmentDto.CountPreview result = evaluationService.evaluateCount("seg-2");

        assertThat(result.getCount()).isEqualTo(100);
        assertThat(result.getExecutionPlan()).isNotNull();
        assertThat(result.getExecutionPlan().getExecutionMode()).isEqualTo("BOUNDED_SQL");
        assertThat(result.getExecutionPlan().isBounded()).isTrue();
        assertThat(result.getExecutionPlan().getConditionCount()).isEqualTo(1);
        assertThat(result.getExecutionPlan().getRequiredIndexes())
                .contains("idx_subscribers_candidate_keyset");
        assertThat(result.getExecutionPlan().getSteps())
                .extracting(SegmentDto.ExecutionPlanStep::getFamily)
                .containsExactly("SUBSCRIBER_FIELD");
        verify(cacheService).set(anyString(), any(), any());
    }

    @Test
    @DisplayName("evaluateCount uses scoped EXISTS for list membership rules")
    void evaluateCount_listMembershipRuleUsesTenantWorkspaceScopedExistsClause() {
        Segment segment = segmentWithRules(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "list_membership", "op", "IN_LIST", "value", "list-1")
        )));

        Query mockQuery = mock(Query.class);
        when(mockQuery.getSingleResult()).thenReturn(5L);

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-list"))
                .thenReturn(Optional.of(segment));
        when(cacheService.get(anyString(), eq(SegmentDto.CountPreview.class)))
                .thenReturn(Optional.empty());
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

        SegmentDto.CountPreview result = evaluationService.evaluateCount("seg-list");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sqlCaptor.capture());
        assertThat(result.getCount()).isEqualTo(5);
        assertThat(sqlCaptor.getValue())
                .contains("EXISTS (SELECT 1 FROM list_memberships lm")
                .contains("lm.tenant_id = :tid")
                .contains("lm.workspace_id = :wid")
                .contains("lm.list_id = :p0")
                .doesNotContain("custom_fields #>> '{list_membership}'");
        verify(mockQuery).setParameter("tid", TENANT_ID);
        verify(mockQuery).setParameter("wid", WORKSPACE_ID);
        verify(mockQuery).setParameter("p0", "list-1");
    }

    @Test
    @DisplayName("evaluateCount uses scoped NOT EXISTS for negative list membership rules")
    void evaluateCount_notInListRuleUsesTenantWorkspaceScopedNotExistsClause() {
        Segment segment = segmentWithRules(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "list_membership", "op", "NOT_IN_LIST", "value", "list-2")
        )));

        Query mockQuery = mock(Query.class);
        when(mockQuery.getSingleResult()).thenReturn(3L);

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-not-list"))
                .thenReturn(Optional.of(segment));
        when(cacheService.get(anyString(), eq(SegmentDto.CountPreview.class)))
                .thenReturn(Optional.empty());
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

        SegmentDto.CountPreview result = evaluationService.evaluateCount("seg-not-list");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sqlCaptor.capture());
        assertThat(result.getCount()).isEqualTo(3);
        assertThat(sqlCaptor.getValue())
                .contains("NOT EXISTS (SELECT 1 FROM list_memberships lm")
                .contains("lm.tenant_id = :tid")
                .contains("lm.workspace_id = :wid")
                .contains("lm.list_id = :p0")
                .doesNotContain("custom_fields #>> '{list_membership}'");
        verify(mockQuery).setParameter("p0", "list-2");
    }

    @Test
    @DisplayName("evaluateCount rejects unsupported condition operators")
    void evaluateCount_rejectsUnsupportedConditionOperator() {
        Segment segment = segmentWithRules(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "email", "op", "MATCHES_REGEX", "value", ".*@example.com")
        )));

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-bad-op"))
                .thenReturn(Optional.of(segment));

        assertThatThrownBy(() -> evaluationService.evaluateCount("seg-bad-op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported segment operator");
        verify(cacheService, never()).get(anyString(), eq(SegmentDto.CountPreview.class));
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("evaluateCount rejects list membership with unsupported operator")
    void evaluateCount_rejectsListMembershipWithUnsupportedOperator() {
        Segment segment = segmentWithRules(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "list_membership", "op", "EQUALS", "value", "list-1")
        )));

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-bad-list"))
                .thenReturn(Optional.of(segment));

        assertThatThrownBy(() -> evaluationService.evaluateCount("seg-bad-list"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("list_membership only supports");
        verify(cacheService, never()).get(anyString(), eq(SegmentDto.CountPreview.class));
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("evaluateCount rejects list operators on non-list fields")
    void evaluateCount_rejectsListOperatorOnNonListField() {
        Segment segment = segmentWithRules(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "email", "op", "IN_LIST", "value", "list-1")
        )));

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-wrong-field"))
                .thenReturn(Optional.of(segment));

        assertThatThrownBy(() -> evaluationService.evaluateCount("seg-wrong-field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List membership operators require");
        verify(cacheService, never()).get(anyString(), eq(SegmentDto.CountPreview.class));
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("evaluateCount rejects relationship traversal before cache lookup")
    void evaluateCount_rejectsRelationshipTraversalBeforeCacheLookup() {
        Segment segment = segmentWithRules(Map.of("operator", "AND", "conditions", List.of(
                Map.of(
                        "field", "email",
                        "op", "EQUALS",
                        "value", "person@example.com",
                        "relationshipPath", "profile.email")
        )));

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-relationship"))
                .thenReturn(Optional.of(segment));

        assertThatThrownBy(() -> evaluationService.evaluateCount("seg-relationship"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data extension relationships");
        verify(cacheService, never()).get(anyString(), eq(SegmentDto.CountPreview.class));
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("explainExecutionPlan returns bounded metadata without query execution")
    void explainExecutionPlan_returnsBoundedMetadataWithoutQueryExecution() {
        Segment segment = segmentWithRules(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "status", "op", "EQUALS", "value", "ACTIVE"),
                Map.of("field", "list_membership", "op", "IN_LIST", "value", "list-1")
        )));

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-plan"))
                .thenReturn(Optional.of(segment));

        SegmentDto.ExecutionPlanPreview preview = evaluationService.explainExecutionPlan("seg-plan");

        assertThat(preview.getSegmentId()).isEqualTo("seg-plan");
        assertThat(preview.getExecutionPlan().getExecutionMode()).isEqualTo("BOUNDED_SQL");
        assertThat(preview.getExecutionPlan().isBounded()).isTrue();
        assertThat(preview.getExecutionPlan().getRequiredIndexes())
                .contains("idx_subscribers_candidate_keyset", "idx_list_memberships_candidate_active");
        assertThat(preview.getExecutionPlan().getSteps())
                .extracting(SegmentDto.ExecutionPlanStep::getStrategy)
                .containsExactly("SUBSCRIBER_FILTER", "SCOPED_EXISTS");
        verify(cacheService, never()).get(anyString(), eq(SegmentDto.CountPreview.class));
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("evaluateCount rejects unapproved predictive segment before query")
    void evaluateCount_rejectsUnapprovedPredictiveSegmentBeforeQuery() {
        Segment segment = segmentWithRules(predictiveRules(Map.of("tenantPolicyEnabled", false)));
        segment.setSegmentType(Segment.SegmentType.PREDICTIVE);

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-predictive"))
                .thenReturn(Optional.of(segment));

        assertThatThrownBy(() -> evaluationService.evaluateCount("seg-predictive"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Predictive segment governance is not approved");
        verify(cacheService, never()).get(anyString(), eq(SegmentDto.CountPreview.class));
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("recompute rejects unapproved predictive segment before membership mutation")
    void recompute_rejectsUnapprovedPredictiveSegmentBeforeMembershipMutation() {
        Segment segment = segmentWithRules(predictiveRules(Map.of("tenantPolicyEnabled", false)));
        segment.setId("seg-predictive");
        segment.setSegmentType(Segment.SegmentType.PREDICTIVE);

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-predictive"))
                .thenReturn(Optional.of(segment));

        assertThatThrownBy(() -> evaluationService.recompute("seg-predictive", TENANT_ID, WORKSPACE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Predictive segment governance is not approved");
        verify(membershipRepository, never()).deleteAllByTenantIdAndWorkspaceIdAndSegmentId(anyString(), anyString(), anyString());
        verify(eventPublisher, never()).publishRecomputed(any());
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("recompute rejects unsupported relationship rules before membership deletion")
    void recompute_rejectsRelationshipRulesBeforeMembershipDeletion() {
        Segment segment = segmentWithRules(Map.of("operator", "AND", "conditions", List.of(
                Map.of(
                        "field", "email",
                        "op", "EQUALS",
                        "value", "person@example.com",
                        "relationshipPath", "profile.email")
        )));
        segment.setId("seg-relationship");

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-relationship"))
                .thenReturn(Optional.of(segment));

        assertThatThrownBy(() -> evaluationService.recompute("seg-relationship", TENANT_ID, WORKSPACE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data extension relationships");
        verify(membershipRepository, never()).deleteAllByTenantIdAndWorkspaceIdAndSegmentId(anyString(), anyString(), anyString());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyList());
        verify(eventPublisher, never()).publishRecomputed(any());
        assertThat(segment.getStatus()).isEqualTo(Segment.SegmentStatus.ERROR);
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("recompute materializes memberships in bounded keyset pages")
    void recompute_materializesMembershipsInBoundedKeysetPages() {
        Segment segment = segmentWithRules(Map.of("operator", "AND", "conditions", List.of(
                Map.of("field", "status", "op", "EQUALS", "value", "ACTIVE")
        )));
        segment.setId("seg-bounded");

        List<String> firstPage = IntStream.range(0, SegmentEvaluationService.RECOMPUTE_MEMBER_PAGE_SIZE)
                .mapToObj(i -> "sub-%04d".formatted(i))
                .toList();
        List<String> secondPage = List.of("sub-1000", "sub-1001");
        Query firstQuery = mock(Query.class);
        Query secondQuery = mock(Query.class);

        when(segmentRepository.findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(TENANT_ID, WORKSPACE_ID, "seg-bounded"))
                .thenReturn(Optional.of(segment));
        when(entityManager.createNativeQuery(anyString())).thenReturn(firstQuery, secondQuery);
        when(firstQuery.getResultList()).thenReturn(firstPage);
        when(secondQuery.getResultList()).thenReturn(secondPage);

        evaluationService.recompute("seg-bounded", TENANT_ID, WORKSPACE_ID);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(2)).createNativeQuery(sqlCaptor.capture());
        assertThat(sqlCaptor.getAllValues().get(0))
                .contains("ORDER BY s.id")
                .doesNotContain("afterSubscriberId");
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("s.id > :afterSubscriberId")
                .contains("ORDER BY s.id");

        verify(firstQuery).setMaxResults(SegmentEvaluationService.RECOMPUTE_MEMBER_PAGE_SIZE);
        verify(secondQuery).setMaxResults(SegmentEvaluationService.RECOMPUTE_MEMBER_PAGE_SIZE);
        verify(secondQuery).setParameter("afterSubscriberId", "sub-0999");

        @SuppressWarnings({ "rawtypes", "unchecked" })
        ArgumentCaptor<List> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate, times(2)).batchUpdate(contains("INSERT INTO segment_memberships"), batchCaptor.capture());
        assertThat(batchCaptor.getAllValues())
                .extracting(List::size)
                .containsExactly(SegmentEvaluationService.RECOMPUTE_MEMBER_PAGE_SIZE, 2);

        assertThat(segment.getMemberCount()).isEqualTo(SegmentEvaluationService.RECOMPUTE_MEMBER_PAGE_SIZE + 2L);
        assertThat(segment.getStatus()).isEqualTo(Segment.SegmentStatus.ACTIVE);
        verify(membershipRepository).deleteAllByTenantIdAndWorkspaceIdAndSegmentId(TENANT_ID, WORKSPACE_ID, "seg-bounded");
        verify(eventPublisher).publishRecomputed(segment);
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("scheduledRecompute scans scheduled segments in bounded id pages")
    void scheduledRecompute_scansScheduledSegmentsInBoundedIdPages() {
        SegmentEvaluationService recomputeDelegate = mock(SegmentEvaluationService.class);
        List<Segment> firstPage = IntStream.range(0, SegmentEvaluationService.SCHEDULED_RECOMPUTE_PAGE_SIZE)
                .mapToObj(i -> scheduledSegment("seg-%03d".formatted(i), "tenant-a", "workspace-a"))
                .toList();
        List<Segment> secondPage = List.of(
                scheduledSegment("seg-100", "tenant-b", "workspace-b"),
                scheduledSegment("seg-101", "tenant-b", "workspace-b"));
        PageRequest boundedRequest = PageRequest.of(0, SegmentEvaluationService.SCHEDULED_RECOMPUTE_PAGE_SIZE);

        when(selfProvider.getObject()).thenReturn(recomputeDelegate);
        when(segmentRepository.findScheduledSegmentsAfterId(isNull(), eq(boundedRequest)))
                .thenReturn(firstPage);
        when(segmentRepository.findScheduledSegmentsAfterId("seg-099", boundedRequest))
                .thenReturn(secondPage);

        evaluationService.scheduledRecompute();

        verify(segmentRepository).findScheduledSegmentsAfterId(isNull(), eq(boundedRequest));
        verify(segmentRepository).findScheduledSegmentsAfterId("seg-099", boundedRequest);
        verify(segmentRepository, never()).findScheduledSegmentsAfterId("seg-101", boundedRequest);
        for (Segment segment : firstPage) {
            verify(recomputeDelegate).recompute(segment.getId(), segment.getTenantId(), segment.getWorkspaceId());
        }
        for (Segment segment : secondPage) {
            verify(recomputeDelegate).recompute(segment.getId(), segment.getTenantId(), segment.getWorkspaceId());
        }
        verify(recomputeDelegate, times(SegmentEvaluationService.SCHEDULED_RECOMPUTE_PAGE_SIZE + secondPage.size()))
                .recompute(anyString(), anyString(), anyString());
        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(TenantContext.getWorkspaceId()).isNull();
    }

    private Segment segmentWithRules(Map<String, Object> rules) {
        Segment segment = new Segment();
        segment.setTenantId(TENANT_ID);
        segment.setWorkspaceId(WORKSPACE_ID);
        segment.setRules(rules);
        return segment;
    }

    private Segment scheduledSegment(String id, String tenantId, String workspaceId) {
        Segment segment = segmentWithRules(Map.of());
        segment.setId(id);
        segment.setTenantId(tenantId);
        segment.setWorkspaceId(workspaceId);
        segment.setScheduleEnabled(true);
        segment.setStatus(Segment.SegmentStatus.ACTIVE);
        return segment;
    }

    private Map<String, Object> predictiveRules(Map<String, Object> governance) {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("operator", "AND");
        rules.put("conditions", List.of(Map.of("field", "status", "op", "EQUALS", "value", "ACTIVE")));
        rules.put(PredictiveSegmentGovernanceService.GOVERNANCE_KEY, governance);
        return rules;
    }
}
