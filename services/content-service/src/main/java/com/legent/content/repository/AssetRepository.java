package com.legent.content.repository;

import com.legent.content.domain.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, String> {

    Page<Asset> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);

    List<Asset> findByTenantIdAndContentTypeAndDeletedAtIsNull(String tenantId, String contentType);

    boolean existsByTenantIdAndFileNameAndDeletedAtIsNull(String tenantId, String fileName);

    @Query("SELECT a FROM Asset a WHERE a.tenantId = :tenantId AND a.deletedAt IS NULL " +
            "AND (:query IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.fileName) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND (:contentType IS NULL OR LOWER(a.contentType) LIKE LOWER(CONCAT('%', :contentType, '%')))")
    Page<Asset> searchAssets(
            @Param("tenantId") String tenantId,
            @Param("query") String query,
            @Param("contentType") String contentType,
            Pageable pageable
    );
}
