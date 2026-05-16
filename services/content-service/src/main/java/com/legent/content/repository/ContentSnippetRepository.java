package com.legent.content.repository;

import com.legent.content.domain.ContentSnippet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentSnippetRepository extends JpaRepository<ContentSnippet, String> {
    Page<ContentSnippet> findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(String tenantId, String workspaceId, Pageable pageable);
    List<ContentSnippet> findByTenantIdAndWorkspaceIdAndDeletedAtIsNull(String tenantId, String workspaceId);
    Optional<ContentSnippet> findByTenantIdAndWorkspaceIdAndSnippetKeyAndDeletedAtIsNull(String tenantId, String workspaceId, String snippetKey);
    Optional<ContentSnippet> findByIdAndTenantIdAndWorkspaceIdAndDeletedAtIsNull(String id, String tenantId, String workspaceId);
    boolean existsByTenantIdAndWorkspaceIdAndSnippetKeyAndDeletedAtIsNull(String tenantId, String workspaceId, String snippetKey);
}
