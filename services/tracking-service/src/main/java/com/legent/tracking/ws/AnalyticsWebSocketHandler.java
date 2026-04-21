package com.legent.tracking.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.scheduling.annotation.Scheduled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import com.legent.common.constant.AppConstants;
import java.io.IOException;


import java.util.List;

import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyticsWebSocketHandler extends TextWebSocketHandler {
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

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
                if (tenantId != null) {
                    try {
                        // For now, we broadcast a heartbeat/placeholder message. 
                        // In a real scenario, this would fetch real-time stats for the tenant.
                        session.sendMessage(new TextMessage("{\"type\":\"HEARTBEAT\",\"tenantId\":\"" + tenantId + "\",\"timestamp\":" + System.currentTimeMillis() + "}"));
                    } catch (IOException e) {
                        log.error("Failed to send analytics message to session {}", session.getId(), e);
                    }
                }
            }
        }
    }
}
