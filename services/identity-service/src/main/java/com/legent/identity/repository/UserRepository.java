package com.legent.identity.repository;

import com.legent.identity.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);
    
    List<User> findByTenantId(String tenantId);

    List<User> findByTenantIdAndIdentityProviderIdAndActiveTrueOrderByEmailAsc(
            String tenantId,
            String identityProviderId,
            Pageable pageable);
    
    Optional<User> findByTenantIdAndId(String tenantId, String id);
    
    boolean existsByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    Optional<User> findFirstByEmailIgnoreCase(String email);

    List<User> findAllByEmailIgnoreCase(String email);

    Optional<User> findByTenantIdAndIdentityProviderIdAndExternalId(String tenantId, String identityProviderId, String externalId);

    Optional<User> findByTenantIdAndIdentityProviderIdAndEmailIgnoreCaseAndActiveTrue(
            String tenantId,
            String identityProviderId,
            String email);
}
