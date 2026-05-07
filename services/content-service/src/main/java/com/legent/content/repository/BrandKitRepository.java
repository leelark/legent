package com.legent.content.repository;

import com.legent.content.domain.BrandKit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandKitRepository extends JpaRepository<BrandKit, String> {
    Page<BrandKit> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);
    List<BrandKit> findByTenantIdAndDeletedAtIsNull(String tenantId);
    Optional<BrandKit> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);
    Optional<BrandKit> findFirstByTenantIdAndIsDefaultTrueAndDeletedAtIsNull(String tenantId);
    boolean existsByTenantIdAndNameAndDeletedAtIsNull(String tenantId, String name);
}
