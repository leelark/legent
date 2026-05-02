package com.legent.delivery.repository;

import java.util.List;

import com.legent.delivery.domain.SmtpProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


@Repository
public interface SmtpProviderRepository extends JpaRepository<SmtpProvider, String> {
    List<SmtpProvider> findByTenantIdAndIsActiveTrueOrderByPriorityAsc(String tenantId);

    @Query("SELECT p FROM SmtpProvider p WHERE p.isActive = true AND p.deletedAt IS NULL")
    List<SmtpProvider> findAllActiveProviders();
}
