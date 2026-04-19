package com.legent.identity.repository;

import com.legent.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);
}
