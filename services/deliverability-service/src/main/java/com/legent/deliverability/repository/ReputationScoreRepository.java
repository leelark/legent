package com.legent.deliverability.repository;

import com.legent.deliverability.domain.ReputationScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReputationScoreRepository extends JpaRepository<ReputationScore, Long> {
    ReputationScore findTopByDomainOrderByLastUpdatedDesc(String domain);

    // AUDIT-017: Add proper query method for single domain lookup
    ReputationScore findByDomain(String domain);
}
