package com.legent.tracking.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.scheduling.annotation.Scheduled;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.tracking.service.AnalyticsService;

import java.util.List;

import java.util.concurrent.CopyOnWriteArrayList;

@Component
@RequiredArgsConstructor
public class AnalyticsWebSocketHandler extends TextWebSocketHandler {
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override

    @Override
    public void afterConnectionEstablished(org.springframework.lang.NonNull WebSocketSession session) {
        sessions.add(session);
    }

    @Override

    @Override
    public void afterConnectionClosed(org.springframework.lang.NonNull WebSocketSession session, org.springframework.lang.NonNull CloseStatus status) {
        sessions.remove(session);
    }

    @Scheduled(fixedRate = 5000)
    public void broadcastAnalytics() throws Exception {
        var data = analyticsService.getEventCounts();
        String json = objectMapper.writeValueAsString(data);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
    }
}
