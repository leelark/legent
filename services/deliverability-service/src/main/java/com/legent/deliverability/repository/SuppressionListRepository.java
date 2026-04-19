package com.legent.deliverability.repository;

import java.util.Optional;

import java.util.List;

import com.legent.deliverability.domain.SuppressionList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SuppressionListRepository extends JpaRepository<SuppressionList, String> {
    List<SuppressionList> findByTenantId(String tenantId);
    Optional<SuppressionList> findByTenantIdAndEmail(String tenantId, String email);
}
