package com.legent.tracking.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import com.legent.common.constant.AppConstants;
import com.legent.tracking.service.AnalyticsService;
import java.io.IOException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsWebSocketHandler extends TextWebSocketHandler {
    private final List<WebSocketSession> sessions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AnalyticsService analyticsService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        sessions.remove(session);
    }

    @Scheduled(fixedRate = 5000)
    public void broadcastAnalytics() {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                String tenantId = (String) session.getAttributes().get(AppConstants.HEADER_TENANT_ID);
                String workspaceId = (String) session.getAttributes().get(AppConstants.HEADER_WORKSPACE_ID);
                if (tenantId != null && workspaceId != null) {
                    try {
                        List<java.util.Map<String, Object>> counts = analyticsService.getEventCounts(tenantId, workspaceId);
                        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(counts)));
                    } catch (IOException e) {
                        log.error("Failed to send analytics message to session {}", session.getId(), e);
                    }
                }
            }
        }
    }
}
