package com.legent.content.repository;

import com.legent.content.domain.ContentBlock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentBlockRepository extends JpaRepository<ContentBlock, String> {

    Page<ContentBlock> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);

    List<ContentBlock> findByTenantIdAndIsGlobalIsTrueAndDeletedAtIsNull(String tenantId);

    boolean existsByTenantIdAndNameAndDeletedAtIsNull(String tenantId, String name);
}
