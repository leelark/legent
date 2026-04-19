package com.legent.campaign.repository;

import java.util.Optional;

import com.legent.campaign.domain.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface CampaignRepository extends JpaRepository<Campaign, String> {
    Optional<Campaign> findByTenantIdAndIdAndDeletedAtIsNull(String tenantId, String id);

    Page<Campaign> findByTenantIdAndDeletedAtIsNull(String tenantId, Pageable pageable);

    Page<Campaign> findByTenantIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
            String tenantId,
            String name,
            Pageable pageable
    );
}
