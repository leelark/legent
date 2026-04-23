package com.legent.deliverability.repository;

import java.util.Optional;

import java.util.List;

import com.legent.deliverability.domain.SenderDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SenderDomainRepository extends JpaRepository<SenderDomain, String> {
    List<SenderDomain> findByTenantId(String tenantId);
    Optional<SenderDomain> findByTenantIdAndDomainName(String tenantId, String domainName);
    Optional<SenderDomain> findByTenantIdAndId(String tenantId, String id);
}
