package com.legent.deliverability.service;

import org.springframework.stereotype.Service;
import java.util.function.Consumer;

@Service
public class DeliverabilityAlertService {
    private Consumer<String> alertHandler = (msg) -> {};

    public void setAlertHandler(Consumer<String> handler) {
        this.alertHandler = handler;
    }

    public void checkAndAlert(double reputation, String domain) {
        if (reputation < 50) {
            alertHandler.accept("Reputation alert for " + domain + ": " + reputation);
        }
    }
}
