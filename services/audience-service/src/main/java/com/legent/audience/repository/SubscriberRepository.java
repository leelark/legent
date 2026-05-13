package com.legent.audience.repository;

import java.util.Optional;

import java.util.List;

import com.legent.audience.domain.Subscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, String> {

    Optional<Subscriber> findByTenantIdAndWorkspaceIdAndSubscriberKeyAndDeletedAtIsNull(String tenantId, String workspaceId, String subscriberKey);

    Optional<Subscriber> findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(String tenantId, String workspaceId, String email);

    boolean existsByTenantIdAndSubscriberKeyAndDeletedAtIsNull(String tenantId, String subscriberKey);

    @Query("SELECT s FROM Subscriber s WHERE s.tenantId = :tid AND s.deletedAt IS NULL")
    Page<Subscriber> findAllByTenant(@Param("tid") String tenantId, Pageable pageable);

    @Query("SELECT s FROM Subscriber s WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL")
    Page<Subscriber> findAllByTenantAndWorkspace(@Param("tid") String tenantId, @Param("wid") String workspaceId, Pageable pageable);

    @Query("""
        SELECT s FROM Subscriber s
        WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL
          AND (:lastSeenId IS NULL OR s.id > :lastSeenId)
        ORDER BY s.id ASC
    """)
    List<Subscriber> findNextByTenantAndWorkspaceAfterId(@Param("tid") String tenantId,
                                                         @Param("wid") String workspaceId,
                                                         @Param("lastSeenId") String lastSeenId,
                                                         Pageable pageable);

    @Query("""
        SELECT s FROM Subscriber s
        WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL
          AND s.status = :status
    """)
    Page<Subscriber> findByTenantAndStatus(@Param("tid") String tenantId,
                                            @Param("wid") String workspaceId,
                                            @Param("status") Subscriber.SubscriberStatus status,
                                            Pageable pageable);

    @Query("""
        SELECT s FROM Subscriber s
        WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL
          AND (LOWER(s.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(s.subscriberKey) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
    Page<Subscriber> searchByTenantAndWorkspace(@Param("tid") String tenantId, @Param("wid") String workspaceId, @Param("q") String query, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Subscriber s WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL")
    long countByTenantAndWorkspace(@Param("tid") String tenantId, @Param("wid") String workspaceId);

    @Query("SELECT COUNT(s) FROM Subscriber s WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.status = :status AND s.deletedAt IS NULL")
    long countByTenantAndWorkspaceAndStatus(@Param("tid") String tenantId, @Param("wid") String workspaceId, @Param("status") Subscriber.SubscriberStatus status);

    List<Subscriber> findByTenantIdAndWorkspaceIdAndEmailInAndDeletedAtIsNull(String tenantId, String workspaceId, List<String> emails);

    List<Subscriber> findByTenantIdAndWorkspaceIdAndIdInAndDeletedAtIsNull(String tenantId, String workspaceId, List<String> ids);

    @Query("SELECT s FROM Subscriber s WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.id = :id AND s.deletedAt IS NULL")
    Optional<Subscriber> findByTenantIdAndWorkspaceIdAndId(@Param("tid") String tenantId, @Param("wid") String workspaceId, @Param("id") String id);

    @Query("SELECT s.id FROM Subscriber s WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL")
    List<String> findIdsByTenantIdAndWorkspaceIdAndDeletedAtIsNull(@Param("tid") String tenantId, @Param("wid") String workspaceId);

    @Query("""
        SELECT LOWER(s.email), COUNT(s.id)
        FROM Subscriber s
        WHERE s.tenantId = :tid AND s.workspaceId = :wid AND s.deletedAt IS NULL
        GROUP BY LOWER(s.email)
        HAVING COUNT(s.id) > 1
    """)
    List<Object[]> findDuplicateEmailsByScope(@Param("tid") String tenantId, @Param("wid") String workspaceId);

    @Query("""
        SELECT s FROM Subscriber s
        WHERE s.tenantId = :tid AND s.workspaceId = :wid AND LOWER(s.email) = LOWER(:email) AND s.deletedAt IS NULL
        ORDER BY s.updatedAt DESC
    """)
    List<Subscriber> findDuplicatesByScopedEmail(@Param("tid") String tenantId, @Param("wid") String workspaceId, @Param("email") String email);

    @Query("""
        SELECT DISTINCT s.tenantId, s.workspaceId
        FROM Subscriber s
        WHERE s.deletedAt IS NULL
    """)
    List<Object[]> findDistinctTenantWorkspaceScopes();
}
