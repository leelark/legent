package com.legent.automation.repository;

import java.util.Optional;

import java.util.List;

import com.legent.automation.domain.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, String> {
    List<Workflow> findByTenantId(String tenantId);
    Optional<Workflow> findByIdAndTenantId(String id, String tenantId);
}
