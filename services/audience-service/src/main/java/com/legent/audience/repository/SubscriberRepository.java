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

    Optional<Subscriber> findByTenantIdAndSubscriberKeyAndDeletedAtIsNull(String tenantId, String subscriberKey);

    Optional<Subscriber> findByTenantIdAndEmailAndDeletedAtIsNull(String tenantId, String email);

    boolean existsByTenantIdAndSubscriberKeyAndDeletedAtIsNull(String tenantId, String subscriberKey);

    @Query("SELECT s FROM Subscriber s WHERE s.tenantId = :tid AND s.deletedAt IS NULL")
    Page<Subscriber> findAllByTenant(@Param("tid") String tenantId, Pageable pageable);

    @Query("""
        SELECT s FROM Subscriber s
        WHERE s.tenantId = :tid AND s.deletedAt IS NULL
          AND s.status = :status
    """)
    Page<Subscriber> findByTenantAndStatus(@Param("tid") String tenantId,
                                            @Param("status") Subscriber.SubscriberStatus status,
                                            Pageable pageable);

    @Query("""
        SELECT s FROM Subscriber s
        WHERE s.tenantId = :tid AND s.deletedAt IS NULL
          AND (LOWER(s.email) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(s.subscriberKey) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
    Page<Subscriber> searchByTenant(@Param("tid") String tenantId, @Param("q") String query, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Subscriber s WHERE s.tenantId = :tid AND s.deletedAt IS NULL")
    long countByTenant(@Param("tid") String tenantId);

    @Query("SELECT COUNT(s) FROM Subscriber s WHERE s.tenantId = :tid AND s.status = :status AND s.deletedAt IS NULL")
    long countByTenantAndStatus(@Param("tid") String tenantId, @Param("status") Subscriber.SubscriberStatus status);

    List<Subscriber> findByTenantIdAndEmailInAndDeletedAtIsNull(String tenantId, List<String> emails);

    @Query("SELECT s FROM Subscriber s WHERE s.tenantId = :tid AND s.id = :id AND s.deletedAt IS NULL")
    Optional<Subscriber> findByTenantIdAndId(@Param("tid") String tenantId, @Param("id") String id);
}
