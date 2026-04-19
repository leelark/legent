package com.legent.deliverability.repository;

import com.legent.deliverability.domain.DomainConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DomainConfigRepository extends JpaRepository<DomainConfig, Long> {
    DomainConfig findByDomain(String domain);
}
