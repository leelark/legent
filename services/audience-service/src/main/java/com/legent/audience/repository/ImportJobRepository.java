package com.legent.audience.repository;

import java.util.Optional;

import com.legent.audience.domain.ImportJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ImportJobRepository extends JpaRepository<ImportJob, String> {

    @Query("SELECT j FROM ImportJob j WHERE j.tenantId = :tid ORDER BY j.createdAt DESC")
    Page<ImportJob> findByTenant(@Param("tid") String tenantId, Pageable pageable);

    Optional<ImportJob> findByTenantIdAndId(String tenantId, String id);
}
