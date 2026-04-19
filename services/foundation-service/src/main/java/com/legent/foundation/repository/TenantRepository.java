package com.legent.foundation.repository;

import java.util.Optional;

import com.legent.foundation.domain.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    @Query("SELECT t FROM Tenant t WHERE t.deletedAt IS NULL")
    Page<Tenant> findAllActive(Pageable pageable);

    @Query("SELECT t FROM Tenant t WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<Tenant> findActiveById(String id);
}
