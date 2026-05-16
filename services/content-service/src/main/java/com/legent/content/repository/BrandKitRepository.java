package com.legent.content.repository;

import com.legent.content.domain.BrandKit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandKitRepository extends JpaRepository<BrandKit, String> {
    Page<BrandKit> findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(String tenantId, String workspaceId, Pageable pageable);
    List<BrandKit> findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(String tenantId, String workspaceId);
    Optional<BrandKit> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);
    Optional<BrandKit> findFirstByTenantIdAndWorkspaceIdAndIsDefaultTrueAndDeletedAtIsNull(String tenantId, String workspaceId);
    boolean existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(String tenantId, String workspaceId, String name);
}
