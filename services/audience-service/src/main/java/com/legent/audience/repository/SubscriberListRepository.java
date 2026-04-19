package com.legent.audience.repository;

import java.util.Optional;

import com.legent.audience.domain.SubscriberList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface SubscriberListRepository extends JpaRepository<SubscriberList, String> {

    @Query("SELECT l FROM SubscriberList l WHERE l.tenantId = :tid AND l.deletedAt IS NULL")
    Page<SubscriberList> findAllByTenant(@Param("tid") String tenantId, Pageable pageable);

    @Query("SELECT l FROM SubscriberList l WHERE l.tenantId = :tid AND l.listType = :type AND l.deletedAt IS NULL")
    Page<SubscriberList> findByTenantAndType(@Param("tid") String tenantId,
                                              @Param("type") SubscriberList.ListType type, Pageable pageable);

    Optional<SubscriberList> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, String id);

    boolean existsByTenantIdAndNameAndDeletedAtIsNull(String tenantId, String name);

    @Query("SELECT COUNT(l) FROM SubscriberList l WHERE l.tenantId = :tid AND l.deletedAt IS NULL")
    long countByTenant(@Param("tid") String tenantId);
}
