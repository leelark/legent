package com.legent.platform.repository;

import java.util.List;

import com.legent.platform.domain.SearchIndexDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SearchIndexDocRepository extends JpaRepository<SearchIndexDoc, String> {
    List<SearchIndexDoc> findByTenantIdAndSearchableTextContainingIgnoreCase(String tenantId, String query);
}
