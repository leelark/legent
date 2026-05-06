package com.legent.identity.repository;

import com.legent.identity.domain.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, String> {
    Optional<UserPreference> findByTenantIdAndUserId(String tenantId, String userId);
}

