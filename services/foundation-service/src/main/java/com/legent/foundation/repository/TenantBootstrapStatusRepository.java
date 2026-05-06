package com.legent.foundation.repository;

import com.legent.foundation.domain.TenantBootstrapStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantBootstrapStatusRepository extends JpaRepository<TenantBootstrapStatus, String> {
}
