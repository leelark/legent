package com.legent.content.repository;

import com.legent.content.domain.ContentBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentBlockRepository extends JpaRepository<ContentBlock, String> {

    Page<ContentBlock> findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(String tenantId, String workspaceId, Pageable pageable);

    List<ContentBlock> findByTenantIdAndWorkspaceIdAndIsGlobalIsTrueAndDeletedAtIsNull(String tenantId, String workspaceId);

    java.util.Optional<ContentBlock> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);

    boolean existsByTenantIdAndWorkspaceIdAndNameAndDeletedAtIsNull(String tenantId, String workspaceId, String name);
}
