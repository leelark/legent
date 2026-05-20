package com.legent.audience.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        when(cacheService.get(anyString(), eq(SegmentDto.CountPreview.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationService.evaluateCount("seg-bad-op"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported segment operator");
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
        when(cacheService.get(anyString(), eq(SegmentDto.CountPreview.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationService.evaluateCount("seg-bad-list"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("list_membership only supports");
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
        when(cacheService.get(anyString(), eq(SegmentDto.CountPreview.class)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluationService.evaluateCount("seg-wrong-field"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("List membership operators require");
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    private Segment segmentWithRules(Map<String, Object> rules) {
        Segment segment = new Segment();
        segment.setTenantId(TENANT_ID);
        segment.setWorkspaceId(WORKSPACE_ID);
        segment.setRules(rules);
        return segment;
    }
}
