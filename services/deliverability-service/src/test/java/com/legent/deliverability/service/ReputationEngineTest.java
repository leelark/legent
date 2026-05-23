package com.legent.deliverability.service;

import com.legent.cache.service.CacheService;
import com.legent.deliverability.domain.DomainReputation;
import com.legent.deliverability.repository.DomainReputationRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReputationEngineTest {

    @Mock private DomainReputationRepository reputationRepository;
    @Mock private CacheService cacheService;

    private ReputationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ReputationEngine(reputationRepository, cacheService);
    }

    @Test
    void recoveryReadsReputationsOneBoundedSliceAtATime() {
        DomainReputation first = reputation("rep-1", "tenant-1", "workspace-1", "domain-1", 90);
        DomainReputation second = reputation("rep-2", "tenant-1", "workspace-1", "domain-2", 99);
        when(reputationRepository.findAllByOrderByIdAsc(any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(0);
                    if (pageable.getPageNumber() == 0) {
                        return new SliceImpl<>(List.of(first), pageable, true);
                    }
                    return new SliceImpl<>(List.of(second), pageable, false);
                });
        when(cacheService.<Long>executeScript(anyRedisScript(), anyList(), any())).thenReturn(0L);

        engine.applyReputationRecovery();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reputationRepository, org.mockito.Mockito.times(2)).findAllByOrderByIdAsc(pageableCaptor.capture());
        assertThat(pageableCaptor.getAllValues())
                .extracting(Pageable::getPageSize)
                .containsExactly(100, 100);
        assertThat(pageableCaptor.getAllValues())
                .extracting(Pageable::getPageNumber)
                .containsExactly(0, 1);

        InOrder inOrder = inOrder(reputationRepository);
        inOrder.verify(reputationRepository).findAllByOrderByIdAsc(pageableCaptor.getAllValues().get(0));
        inOrder.verify(reputationRepository).save(first);
        inOrder.verify(reputationRepository).findAllByOrderByIdAsc(pageableCaptor.getAllValues().get(1));
        inOrder.verify(reputationRepository).save(second);
        assertThat(first.getReputationScore()).isEqualTo(91);
        assertThat(second.getReputationScore()).isEqualTo(100);
    }

    @Test
    void recoverySkipsRowsMissingWorkspaceContext() {
        DomainReputation missingWorkspace = reputation("rep-1", "tenant-1", " ", "domain-1", 90);
        when(reputationRepository.findAllByOrderByIdAsc(any(Pageable.class)))
                .thenAnswer(invocation -> new SliceImpl<>(
                        List.of(missingWorkspace),
                        invocation.getArgument(0),
                        false));

        engine.applyReputationRecovery();

        verify(cacheService, never()).executeScript(anyRedisScript(), anyList(), any());
        verify(reputationRepository, never()).save(any(DomainReputation.class));
        assertThat(missingWorkspace.getReputationScore()).isEqualTo(90);
    }

    @Test
    void recoveryFailsClosedWithoutSavingWhenCacheCountsCannotBeRead() {
        DomainReputation reputation = reputation("rep-1", "tenant-1", "workspace-1", "domain-1", 90);
        when(reputationRepository.findAllByOrderByIdAsc(any(Pageable.class)))
                .thenAnswer(invocation -> new SliceImpl<>(
                        List.of(reputation),
                        invocation.getArgument(0),
                        false));
        when(cacheService.<Long>executeScript(anyRedisScript(), anyList(), any()))
                .thenThrow(new IllegalStateException("redis unavailable"));

        engine.applyReputationRecovery();

        verify(reputationRepository, never()).save(any(DomainReputation.class));
        assertThat(reputation.getReputationScore()).isEqualTo(90);
    }

    @Test
    void recoveryDoesNotSaveWhenRecentNegativeEventsExist() {
        DomainReputation reputation = reputation("rep-1", "tenant-1", "workspace-1", "domain-1", 90);
        when(reputationRepository.findAllByOrderByIdAsc(any(Pageable.class)))
                .thenAnswer(invocation -> new SliceImpl<>(
                        List.of(reputation),
                        invocation.getArgument(0),
                        false));
        when(cacheService.<Long>executeScript(anyRedisScript(), anyList(), any()))
                .thenReturn(1L, 0L);

        engine.applyReputationRecovery();

        verify(reputationRepository, never()).save(any(DomainReputation.class));
        assertThat(reputation.getReputationScore()).isEqualTo(90);
    }

    private DomainReputation reputation(
            String id,
            String tenantId,
            String workspaceId,
            String domainId,
            int score) {
        DomainReputation reputation = new DomainReputation();
        reputation.setId(id);
        reputation.setTenantId(tenantId);
        reputation.setWorkspaceId(workspaceId);
        reputation.setDomainId(domainId);
        reputation.setOwnershipScope("WORKSPACE");
        reputation.setReputationScore(score);
        reputation.setHardBounceRate(BigDecimal.ZERO);
        reputation.setComplaintRate(BigDecimal.ZERO);
        return reputation;
    }

    @SuppressWarnings("unchecked")
    private RedisScript<Long> anyRedisScript() {
        return (RedisScript<Long>) any(RedisScript.class);
    }
}
