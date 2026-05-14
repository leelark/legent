package com.legent.audience.repository;

import java.util.Optional;

import com.legent.audience.domain.DataExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface DataExtensionRepository extends JpaRepository<DataExtension, String> {

    @Query("SELECT d FROM DataExtension d WHERE d.tenantId = :tid AND d.workspaceId = :wid AND d.deletedAt IS NULL")
    Page<DataExtension> findAllByTenantAndWorkspace(@Param("tid") String tenantId,
                                                    @Param("wid") String workspaceId,
                                                    Pageable pageable);

    Optional<DataExtension> findByTenantIdAndWorkspaceIdAndIdAndDeletedAtIsNull(String tenantId, String workspaceId, String id);

    @Query("SELECT COUNT(d) > 0 FROM DataExtension d WHERE d.tenantId = :tid AND d.workspaceId = :wid AND lower(d.name) = lower(:name) AND d.deletedAt IS NULL")
    boolean existsByTenantWorkspaceAndName(@Param("tid") String tenantId,
                                           @Param("wid") String workspaceId,
                                           @Param("name") String name);

    @Query("SELECT COUNT(d) FROM DataExtension d WHERE d.tenantId = :tid AND d.workspaceId = :wid AND d.deletedAt IS NULL")
    long countByTenantAndWorkspace(@Param("tid") String tenantId, @Param("wid") String workspaceId);
}
