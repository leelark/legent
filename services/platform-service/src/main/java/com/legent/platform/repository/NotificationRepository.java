package com.legent.platform.repository;

import java.util.List;

import com.legent.platform.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByTenantIdAndIsReadFalseOrderByCreatedAtDesc(String tenantId);
}
