package com.legent.foundation.repository;

import java.util.Optional;

import java.util.List;

import com.legent.foundation.domain.FeatureFlag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, String> {

    /**
     * Find flag by key — tenant-specific first, then global fallback.
     * Used by the feature flag resolution hierarchy.
     */
    @Query("""
        SELECT f FROM FeatureFlag f
        WHERE f.flagKey = :key
          AND (f.tenantId = :tenantId OR f.tenantId IS NULL)
          AND f.deletedAt IS NULL
        ORDER BY f.tenantId DESC NULLS LAST
    """)
    List<FeatureFlag> findByKeyWithFallback(
            @Param("key") String flagKey,
            @Param("tenantId") String tenantId);

    @Query("SELECT f FROM FeatureFlag f WHERE f.tenantId = :tenantId AND f.deletedAt IS NULL")
    Page<FeatureFlag> findByTenantId(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT f FROM FeatureFlag f WHERE f.tenantId IS NULL AND f.deletedAt IS NULL")
    Page<FeatureFlag> findGlobalFlags(Pageable pageable);

    Optional<FeatureFlag> findByTenantIdAndFlagKeyAndDeletedAtIsNull(String tenantId, String flagKey);
}
