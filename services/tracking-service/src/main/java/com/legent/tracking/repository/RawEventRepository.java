package com.legent.tracking.repository;

import java.util.List;

import com.legent.tracking.domain.RawEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface RawEventRepository extends JpaRepository<RawEvent, String> {
    List<RawEvent> findByTenantIdAndCampaignIdAndEventType(String tenantId, String campaignId, String eventType);
}
