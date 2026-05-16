package com.legent.platform.controller;

import com.legent.platform.domain.TenantConfig;
import com.legent.platform.service.GlobalSearchService;
import com.legent.platform.service.FoundationSettingsBridgeService;
import com.legent.platform.service.NotificationEngine;
import com.legent.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformControllerTenantContextTest {

    @Mock private FoundationSettingsBridgeService bridgeService;
    @Mock private NotificationEngine notificationEngine;
    @Mock private GlobalSearchService searchService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void platformConfigUsesAuthenticatedTenantAndWorkspaceContext() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantConfig config = new TenantConfig();
        config.setTenantId("tenant-1");
        when(bridgeService.loadTenantConfig("tenant-1", "workspace-1")).thenReturn(config);

        new PlatformConfigController(bridgeService).getConfig();

        verify(bridgeService).loadTenantConfig("tenant-1", "workspace-1");
    }

    @Test
    void platformConfigFailsClosedWithoutWorkspaceContext() {
        TenantContext.setTenantId("tenant-1");

        assertThrows(IllegalStateException.class,
                () -> new PlatformConfigController(bridgeService).getConfig());
    }

    @Test
    void platformConfigFailsClosedWhenFoundationBridgeFails() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        when(bridgeService.loadTenantConfig("tenant-1", "workspace-1"))
                .thenThrow(new IllegalStateException("foundation unavailable"));

        assertThrows(IllegalStateException.class,
                () -> new PlatformConfigController(bridgeService).getConfig());
    }

    @Test
    void notificationsUseAuthenticatedTenantAndUserContext() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");
        TenantContext.setUserId("user-1");

        new NotificationController(notificationEngine).getUnreadNotifications();

        verify(notificationEngine).getUnreadNotifications("tenant-1", "workspace-1", "user-1");
    }

    @Test
    void notificationsFailClosedWithoutWorkspaceContext() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setUserId("user-1");

        assertThrows(IllegalStateException.class,
                () -> new NotificationController(notificationEngine).getUnreadNotifications());
    }

    @Test
    void searchUsesAuthenticatedTenantAndWorkspaceContext() {
        TenantContext.setTenantId("tenant-1");
        TenantContext.setWorkspaceId("workspace-1");

        new SearchController(searchService).search("campaign");

        verify(searchService).search("tenant-1", "workspace-1", "campaign");
    }

    @Test
    void searchFailsClosedWithoutWorkspaceContext() {
        TenantContext.setTenantId("tenant-1");

        assertThrows(IllegalStateException.class,
                () -> new SearchController(searchService).search("campaign"));
    }
}
