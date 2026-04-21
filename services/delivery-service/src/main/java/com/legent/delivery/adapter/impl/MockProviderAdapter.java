package com.legent.delivery.adapter.impl;

import java.util.Map;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.domain.SmtpProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

@Slf4j
@Component
public class MockProviderAdapter implements ProviderAdapter {

    private final Random random = new Random();

    @Override
    public String getProviderType() {
        return "MOCK";
    }

    @Override
    public void sendEmail(@org.springframework.lang.NonNull String to, @org.springframework.lang.NonNull String subject, @org.springframework.lang.NonNull String htmlBody, Map<String, String> metadata, SmtpProvider config) throws ProviderDispatchException {
        // Simulate network latency (20ms)
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate 95% success, 4% transient (retryable), 1% permanent (hard bounce)
        int outcome = random.nextInt(100);
        
        if (outcome < 1) {
            log.warn("Mock provider HARD_BOUNCE for {}", to);
            throw new ProviderDispatchException("Mock Provider: 550 Mailbox unavailable", true);
        } else if (outcome < 5) {
            log.warn("Mock provider TRANSIENT fallback for {}", to);
            throw new ProviderDispatchException("Mock Provider: 421 Connection timeout", false);
        }
        
        log.debug("Mock provider successfully delivered to {}", to);
    }
}
