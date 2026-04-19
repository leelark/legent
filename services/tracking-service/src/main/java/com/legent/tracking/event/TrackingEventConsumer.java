package com.legent.tracking.event;

import com.legent.tracking.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrackingEventConsumer {
    private final TrackingEventRepository repository;

    @KafkaListener(topics = "email.open", groupId = "tracking")
    public void consumeOpen(String mid) {
        repository.saveOpen(mid);
    }

    @KafkaListener(topics = "email.click", groupId = "tracking")
    public void consumeClick(String payload) {
        String[] parts = payload.split("\\|", 2);
        repository.saveClick(parts[0], parts.length > 1 ? parts[1] : null);
    }

    @KafkaListener(topics = "conversion.event", groupId = "tracking")
    public void consumeConversion(String payload) {
        String[] parts = payload.split("\\|", 2);
        repository.saveConversion(parts[0], parts.length > 1 ? parts[1] : null);
    }
}
