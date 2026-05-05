package com.legent.identity.repository;

import com.legent.identity.domain.AuthInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthInvitationRepository extends JpaRepository<AuthInvitation, String> {
    List<AuthInvitation> findByTenantIdOrderByCreatedAtDesc(String tenantId);
    Optional<AuthInvitation> findByToken(String token);
}
