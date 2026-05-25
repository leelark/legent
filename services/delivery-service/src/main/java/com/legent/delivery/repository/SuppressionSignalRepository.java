package com.legent.delivery.repository;

import com.legent.delivery.domain.SuppressionSignal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SuppressionSignalRepository extends JpaRepository<SuppressionSignal, String> {

    Optional<SuppressionSignal> findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndTypeAndDeletedAtIsNull(
            String tenantId,
            String workspaceId,
            String email,
            String type);
}
