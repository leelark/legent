package com.legent.tracking.event;

import com.legent.tracking.repository.TrackingEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TrackingEventConsumerTest {
    @Test
    void consumeOpen_savesOpen() {
        var repo = Mockito.mock(TrackingEventRepository.class);
        var consumer = new TrackingEventConsumer(repo);
        consumer.consumeOpen("mid123");
        Mockito.verify(repo).saveOpen("mid123");
    }

    @Test
    void consumeClick_savesClick() {
        var repo = Mockito.mock(TrackingEventRepository.class);
        var consumer = new TrackingEventConsumer(repo);
        consumer.consumeClick("mid123|https://example.com");
        Mockito.verify(repo).saveClick("mid123", "https://example.com");
    }

    @Test
    void consumeConversion_savesConversion() {
        var repo = Mockito.mock(TrackingEventRepository.class);
        var consumer = new TrackingEventConsumer(repo);
        consumer.consumeConversion("mid123|{\"amount\":100}");
        Mockito.verify(repo).saveConversion("mid123", "{\"amount\":100}");
    }
}
