package com.legent.content.repository;

import com.legent.content.domain.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, String> {

    Page<Asset> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);

    List<Asset> findByTenantIdAndContentTypeAndDeletedAtIsNull(String tenantId, String contentType);

    boolean existsByTenantIdAndFileNameAndDeletedAtIsNull(String tenantId, String fileName);
}
