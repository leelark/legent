package com.legent.audience.repository;

import java.util.List;

import com.legent.audience.domain.DataExtensionRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataExtensionRelationshipRepository extends JpaRepository<DataExtensionRelationship, String> {

    List<DataExtensionRelationship> findByTenantIdAndWorkspaceIdAndSourceDataExtensionIdAndDeletedAtIsNullOrderByOrdinalAscNameAsc(
            String tenantId,
            String workspaceId,
            String sourceDataExtensionId);

    List<DataExtensionRelationship> findByTenantIdAndWorkspaceIdAndTargetDataExtensionIdAndDeletedAtIsNullOrderByOrdinalAscNameAsc(
            String tenantId,
            String workspaceId,
            String targetDataExtensionId);
}
