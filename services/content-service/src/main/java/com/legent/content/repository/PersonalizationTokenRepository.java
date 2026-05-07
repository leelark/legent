package com.legent.content.repository;

import com.legent.content.domain.PersonalizationToken;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PersonalizationTokenRepository extends JpaRepository<PersonalizationToken, String> {
    Page<PersonalizationToken> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);
    List<PersonalizationToken> findByTenantIdAndDeletedAtIsNull(String tenantId);
    Optional<PersonalizationToken> findByTenantIdAndTokenKeyAndDeletedAtIsNull(String tenantId, String tokenKey);
    Optional<PersonalizationToken> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);
    boolean existsByTenantIdAndTokenKeyAndDeletedAtIsNull(String tenantId, String tokenKey);
}
