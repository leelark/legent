package com.legent.audience.repository;

import com.legent.audience.domain.SubscriberIdentityProvenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriberIdentityProvenanceRepository extends JpaRepository<SubscriberIdentityProvenance, String> {
}
