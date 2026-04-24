package com.legent.content.repository;

import com.legent.content.domain.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailRepository extends JpaRepository<Email, String> {
    Page<Email> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    List<Email> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(String tenantId);

    Optional<Email> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);
}
