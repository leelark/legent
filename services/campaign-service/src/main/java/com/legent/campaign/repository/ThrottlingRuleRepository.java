package com.legent.campaign.repository;

import java.util.Optional;

import java.util.List;

import com.legent.campaign.domain.ThrottlingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ThrottlingRuleRepository extends JpaRepository<ThrottlingRule, String> {
    Optional<ThrottlingRule> findByTenantIdAndDomain(String tenantId, String domain);
    List<ThrottlingRule> findByTenantIdAndEnabledTrue(String tenantId);
}
