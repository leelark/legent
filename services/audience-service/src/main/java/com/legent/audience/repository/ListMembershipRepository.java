package com.legent.audience.repository;

import java.util.Optional;


import com.legent.audience.domain.ListMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface ListMembershipRepository extends JpaRepository<ListMembership, String> {

    @Query("SELECT m FROM ListMembership m WHERE m.listId = :listId AND m.status = 'ACTIVE'")
    Page<ListMembership> findActiveByList(@Param("listId") String listId, Pageable pageable);

    Optional<ListMembership> findByListIdAndSubscriberId(String listId, String subscriberId);

    boolean existsByListIdAndSubscriberIdAndStatus(String listId, String subscriberId, ListMembership.MembershipStatus status);

    @Query("SELECT COUNT(m) FROM ListMembership m WHERE m.listId = :listId AND m.status = 'ACTIVE'")
    long countActiveByList(@Param("listId") String listId);

    @Query("""
        SELECT m.subscriberId FROM ListMembership m
        WHERE m.tenantId = :tenantId
          AND m.listId = :listId
          AND m.status = 'ACTIVE'
    """)
    java.util.List<String> findActiveSubscriberIdsByTenantAndListId(@Param("tenantId") String tenantId, @Param("listId") String listId);

    @Modifying
    @Query("DELETE FROM ListMembership m WHERE m.listId = :listId AND m.subscriberId IN :subIds")
    void removeMembers(@Param("listId") String listId, @Param("subIds") java.util.List<String> subscriberIds);
}
