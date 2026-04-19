package com.legent.audience.service;

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

    @BeforeEach
    void setUp() { TenantContext.setTenantId(TENANT_ID); }

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

        when(segmentRepository.findByTenantIdAndIdAndDeletedAtIsNull(TENANT_ID, "seg-1"))
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

        when(segmentRepository.findByTenantIdAndIdAndDeletedAtIsNull(TENANT_ID, "seg-2"))
                .thenReturn(Optional.of(segment));
        when(cacheService.get(anyString(), eq(SegmentDto.CountPreview.class)))
                .thenReturn(Optional.empty());
        when(entityManager.createNativeQuery(anyString())).thenReturn(mockQuery);

        SegmentDto.CountPreview result = evaluationService.evaluateCount("seg-2");

        assertThat(result.getCount()).isEqualTo(100);
        verify(cacheService).set(anyString(), any(), any());
    }
}
