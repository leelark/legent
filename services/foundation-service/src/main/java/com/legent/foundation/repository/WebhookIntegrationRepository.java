package com.legent.foundation.repository;

import com.legent.foundation.domain.WebhookIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookIntegrationRepository extends JpaRepository<WebhookIntegration, Long> {
}
