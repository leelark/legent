package com.legent.audience.repository;

import java.util.Optional;

import com.legent.audience.domain.DataExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface DataExtensionRepository extends JpaRepository<DataExtension, String> {

    @Query("SELECT d FROM DataExtension d WHERE d.tenantId = :tid AND d.deletedAt IS NULL")
    Page<DataExtension> findAllByTenant(@Param("tid") String tenantId, Pageable pageable);

    Optional<DataExtension> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, String id);

    boolean existsByTenantIdAndNameAndDeletedAtIsNull(String tenantId, String name);

    @Query("SELECT COUNT(d) FROM DataExtension d WHERE d.tenantId = :tid AND d.deletedAt IS NULL")
    long countByTenant(@Param("tid") String tenantId);
}
