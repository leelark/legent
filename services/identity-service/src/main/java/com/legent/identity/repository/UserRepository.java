package com.legent.identity.repository;

import com.legent.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);
    
    java.util.List<User> findByTenantId(String tenantId);
    
    Optional<User> findByTenantIdAndId(String tenantId, String id);
    
    boolean existsByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    Optional<User> findFirstByEmailIgnoreCase(String email);

    Optional<User> findByTenantIdAndIdentityProviderIdAndExternalId(String tenantId, String identityProviderId, String externalId);
}
