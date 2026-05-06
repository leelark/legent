package com.legent.identity.repository;

import com.legent.identity.domain.OnboardingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OnboardingStateRepository extends JpaRepository<OnboardingState, String> {
    Optional<OnboardingState> findByTenantIdAndUserId(String tenantId, String userId);
}

