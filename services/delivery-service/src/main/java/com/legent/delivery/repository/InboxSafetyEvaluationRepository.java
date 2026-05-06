package com.legent.delivery.repository;

import com.legent.delivery.domain.InboxSafetyEvaluation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboxSafetyEvaluationRepository extends JpaRepository<InboxSafetyEvaluation, String> {
    List<InboxSafetyEvaluation> findByTenantIdAndWorkspaceIdOrderByCreatedAtDesc(String tenantId, String workspaceId, Pageable pageable);
}
