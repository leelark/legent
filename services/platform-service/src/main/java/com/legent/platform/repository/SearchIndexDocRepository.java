package com.legent.platform.repository;

import com.legent.platform.domain.SearchIndexDoc;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SearchIndexDocRepository extends JpaRepository<SearchIndexDoc, String> {
    Slice<SearchIndexDoc> findByTenantIdAndWorkspaceIdAndSearchableTextContainingIgnoreCase(
            String tenantId, String workspaceId, String query, Pageable pageable);
}
