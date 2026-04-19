package com.legent.delivery.repository;

import java.util.Optional;

import com.legent.delivery.domain.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, String> {
    Optional<RoutingRule> findByTenantIdAndSenderDomainIgnoreCaseAndIsActiveTrue(String tenantId, String senderDomain);
}
