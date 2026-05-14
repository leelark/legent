package com.legent.audience.repository;

import com.legent.audience.domain.ListMembership;
import com.legent.audience.domain.Subscriber;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AudienceCandidateRepositoryImpl implements AudienceCandidateRepository {

    private final EntityManager entityManager;

    @Override
    public List<Subscriber> findNextCandidates(
            String tenantId,
            String workspaceId,
            AudienceCandidateCriteria criteria,
            String lastSeenId,
            Pageable pageable) {
        if (!criteria.includeAllSubscribers()
                && criteria.includeListIds().isEmpty()
                && criteria.includeSegmentIds().isEmpty()) {
            return List.of();
        }

        StringBuilder jpql = new StringBuilder("""
                SELECT s FROM Subscriber s
                WHERE s.tenantId = :tenantId
                  AND s.workspaceId = :workspaceId
                  AND s.deletedAt IS NULL
                """);
        if (lastSeenId != null) {
            jpql.append(" AND s.id > :lastSeenId");
        }
        appendIncludeCriteria(jpql, criteria);
        appendExcludeCriteria(jpql, criteria);
        jpql.append(" ORDER BY s.id ASC");

        TypedQuery<Subscriber> query = entityManager.createQuery(jpql.toString(), Subscriber.class);
        query.setParameter("tenantId", tenantId);
        query.setParameter("workspaceId", workspaceId);
        if (lastSeenId != null) {
            query.setParameter("lastSeenId", lastSeenId);
        }
        if (!criteria.includeListIds().isEmpty()) {
            query.setParameter("includeListIds", criteria.includeListIds());
            query.setParameter("activeMembershipStatus", ListMembership.MembershipStatus.ACTIVE);
        }
        if (!criteria.includeSegmentIds().isEmpty()) {
            query.setParameter("includeSegmentIds", criteria.includeSegmentIds());
        }
        if (!criteria.excludeListIds().isEmpty()) {
            query.setParameter("excludeListIds", criteria.excludeListIds());
            query.setParameter("activeMembershipStatus", ListMembership.MembershipStatus.ACTIVE);
        }
        if (!criteria.excludeSegmentIds().isEmpty()) {
            query.setParameter("excludeSegmentIds", criteria.excludeSegmentIds());
        }
        query.setMaxResults(pageable.getPageSize());
        return query.getResultList();
    }

    private void appendIncludeCriteria(StringBuilder jpql, AudienceCandidateCriteria criteria) {
        if (criteria.includeAllSubscribers()) {
            return;
        }

        jpql.append(" AND (");
        boolean hasPrevious = false;
        if (!criteria.includeListIds().isEmpty()) {
            jpql.append("""
                    EXISTS (
                        SELECT 1 FROM ListMembership lm
                        WHERE lm.tenantId = s.tenantId
                          AND lm.workspaceId = s.workspaceId
                          AND lm.subscriberId = s.id
                          AND lm.listId IN :includeListIds
                          AND lm.status = :activeMembershipStatus
                    )
                    """);
            hasPrevious = true;
        }
        if (!criteria.includeSegmentIds().isEmpty()) {
            if (hasPrevious) {
                jpql.append(" OR ");
            }
            jpql.append("""
                    EXISTS (
                        SELECT 1 FROM SegmentMembership sm
                        WHERE sm.tenantId = s.tenantId
                          AND sm.workspaceId = s.workspaceId
                          AND sm.subscriberId = s.id
                          AND sm.segmentId IN :includeSegmentIds
                    )
                    """);
        }
        jpql.append(")");
    }

    private void appendExcludeCriteria(StringBuilder jpql, AudienceCandidateCriteria criteria) {
        if (!criteria.excludeListIds().isEmpty()) {
            jpql.append("""
                     AND NOT EXISTS (
                        SELECT 1 FROM ListMembership excludedLm
                        WHERE excludedLm.tenantId = s.tenantId
                          AND excludedLm.workspaceId = s.workspaceId
                          AND excludedLm.subscriberId = s.id
                          AND excludedLm.listId IN :excludeListIds
                          AND excludedLm.status = :activeMembershipStatus
                    )
                    """);
        }
        if (!criteria.excludeSegmentIds().isEmpty()) {
            jpql.append("""
                     AND NOT EXISTS (
                        SELECT 1 FROM SegmentMembership excludedSm
                        WHERE excludedSm.tenantId = s.tenantId
                          AND excludedSm.workspaceId = s.workspaceId
                          AND excludedSm.subscriberId = s.id
                          AND excludedSm.segmentId IN :excludeSegmentIds
                    )
                    """);
        }
    }
}
