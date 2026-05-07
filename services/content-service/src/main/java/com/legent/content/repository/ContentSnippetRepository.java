package com.legent.content.repository;

import com.legent.content.domain.ContentSnippet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentSnippetRepository extends JpaRepository<ContentSnippet, String> {
    Page<ContentSnippet> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);
    List<ContentSnippet> findByTenantIdAndDeletedAtIsNull(String tenantId);
    Optional<ContentSnippet> findByTenantIdAndSnippetKeyAndDeletedAtIsNull(String tenantId, String snippetKey);
    Optional<ContentSnippet> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);
    boolean existsByTenantIdAndSnippetKeyAndDeletedAtIsNull(String tenantId, String snippetKey);
}
