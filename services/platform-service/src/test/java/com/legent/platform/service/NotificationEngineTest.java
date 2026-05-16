package com.legent.platform.service;

import com.legent.platform.domain.Notification;
import com.legent.platform.repository.NotificationRepository;
import com.legent.security.TenantContext;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
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
    void getUnreadNotificationsFiltersByTenantWorkspaceAndUser() {
        NotificationEngine service = new NotificationEngine(notificationRepository);

        service.getUnreadNotifications("tenant-1", "workspace-1", "user-1");

        verify(notificationRepository).findByTenantIdAndWorkspaceIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(
                "tenant-1", "workspace-1", "user-1");
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
