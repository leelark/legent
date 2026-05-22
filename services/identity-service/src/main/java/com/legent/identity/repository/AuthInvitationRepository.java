package com.legent.identity.repository;

import com.legent.identity.domain.AuthInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthInvitationRepository extends JpaRepository<AuthInvitation, String> {
    List<AuthInvitation> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(String tenantId, String workspaceId);
    Optional<AuthInvitation> findByToken(String token);
}
