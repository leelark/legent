package com.legent.audience.repository;

import com.legent.audience.domain.Subscriber;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

public interface AudienceCandidateRepository {

    List<Subscriber> findNextCandidates(
            String tenantId,
            String workspaceId,
            AudienceCandidateCriteria criteria,
            String lastSeenId,
            Pageable pageable);

    record AudienceCandidateCriteria(
            boolean includeAllSubscribers,
            Set<String> includeListIds,
            Set<String> includeSegmentIds,
            Set<String> excludeListIds,
            Set<String> excludeSegmentIds) {

        public AudienceCandidateCriteria {
            includeListIds = Set.copyOf(includeListIds);
            includeSegmentIds = Set.copyOf(includeSegmentIds);
            excludeListIds = Set.copyOf(excludeListIds);
            excludeSegmentIds = Set.copyOf(excludeSegmentIds);
        }
    }
}
