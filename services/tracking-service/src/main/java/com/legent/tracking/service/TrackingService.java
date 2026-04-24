package com.legent.tracking.service;

import com.legent.tracking.event.TrackingEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrackingService {
    private final TrackingEventPublisher eventPublisher;

    public void handleOpen(String mid, String tenantId) {
        eventPublisher.publishOpen(mid, tenantId);
    }

    public void handleClick(String mid, String url, String tenantId) {
        eventPublisher.publishClick(mid, url, tenantId);
    }

    public void handleConversion(String mid, String payload, String tenantId) {
        eventPublisher.publishConversion(mid, payload, tenantId);
    }
}
