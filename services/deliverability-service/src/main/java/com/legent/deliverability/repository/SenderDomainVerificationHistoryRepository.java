package com.legent.deliverability.repository;

import com.legent.deliverability.domain.SenderDomainVerificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SenderDomainVerificationHistoryRepository extends JpaRepository<SenderDomainVerificationHistory, String> {
}
