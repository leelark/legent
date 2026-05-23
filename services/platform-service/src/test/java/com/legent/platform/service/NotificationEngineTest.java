package com.legent.platform.service;

import com.legent.platform.domain.Notification;
import com.legent.platform.repository.NotificationRepository;
import com.legent.security.TenantContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEngineTest {

    @Mock private NotificationRepository notificationRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createNotificationUsesWorkspaceContextWhenAvailable() {
        TenantContext.setWorkspaceId("workspace-1");
        NotificationEngine service = new NotificationEngine(notificationRepository);

        service.createNotification("tenant-1", "user-1", "Title", "Message", "INFO", "/app");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-1");
        assertThat(captor.getValue().getWorkspaceId()).isEqualTo("workspace-1");
    }

    @Test
    void createNotificationWithoutWorkspacePreservesTenantGlobalRecord() {
        NotificationEngine service = new NotificationEngine(notificationRepository);

        service.createNotification("tenant-1", "user-1", "Title", "Message", "INFO", "/app");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getWorkspaceId()).isNull();
    }

    @Test
    void getUnreadNotificationsReadsBoundedFirstPageByTenantWorkspaceAndUser() {
        NotificationEngine service = new NotificationEngine(notificationRepository);
        Notification notification = new Notification();
        notification.setId("notification-1");
        when(notificationRepository.findByTenantIdAndWorkspaceIdAndUserIdAndIsReadFalse(
                eq("tenant-1"), eq("workspace-1"), eq("user-1"), any(Pageable.class)))
                .thenReturn(List.of(notification));

        List<Notification> result = service.getUnreadNotifications("tenant-1", "workspace-1", "user-1");

        assertThat(result).containsExactly(notification);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByTenantIdAndWorkspaceIdAndUserIdAndIsReadFalse(
                eq("tenant-1"), eq("workspace-1"), eq("user-1"), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        Sort.Order createdAtOrder = pageable.getSort().getOrderFor("createdAt");
        assertThat(createdAtOrder).isNotNull();
        assertThat(createdAtOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void markAsReadFindsNotificationByTenantWorkspaceAndUser() {
        Notification notification = new Notification();
        notification.setId("notification-1");
        when(notificationRepository.findByIdAndTenantIdAndWorkspaceIdAndUserId(
                "notification-1", "tenant-1", "workspace-1", "user-1"))
                .thenReturn(Optional.of(notification));
        NotificationEngine service = new NotificationEngine(notificationRepository);

        service.markAsRead("notification-1", "tenant-1", "workspace-1", "user-1");

        verify(notificationRepository).findByIdAndTenantIdAndWorkspaceIdAndUserId(
                "notification-1", "tenant-1", "workspace-1", "user-1");
        assertThat(notification.getIsRead()).isTrue();
        verify(notificationRepository).save(notification);
    }
}
