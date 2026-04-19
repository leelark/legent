package com.legent.foundation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void notifyAll(String message) {
        for (var l : listeners) l.accept(message);
    }
}
