package com.legent.deliverability.repository;

import java.util.Optional;

import com.legent.deliverability.domain.DomainReputation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface DomainReputationRepository extends JpaRepository<DomainReputation, String> {
    Optional<DomainReputation> findByDomainId(String domainId);
}
