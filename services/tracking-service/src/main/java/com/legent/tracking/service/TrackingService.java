package com.legent.tracking.service;

import com.legent.tracking.event.TrackingEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrackingService {
    private final TrackingEventPublisher eventPublisher;

    public void handleOpen(String mid) {
        eventPublisher.publishOpen(mid);
    }

    public void handleClick(String mid, String url) {
        eventPublisher.publishClick(mid, url);
    }

    public void handleConversion(String mid, String payload) {
        eventPublisher.publishConversion(mid, payload);
    }
}
