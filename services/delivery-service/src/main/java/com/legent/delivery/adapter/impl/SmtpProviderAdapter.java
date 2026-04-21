package com.legent.delivery.adapter.impl;

import java.util.Map;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.domain.SmtpProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpProviderAdapter implements ProviderAdapter {

    // Note: In a true multi-tenant dynamic system, we wouldn't use standard auto-configured JavaMailSender
    // Instead we'd create dynamic JavaMailSender instances based on tenant's SmtpProvider db record.
    // Simplifying here to use the default configured one for illustration.
    private final JavaMailSender javaMailSender;

    // Throttling state: tenant+provider -> [windowStart, sentCount]
    private static final ConcurrentHashMap<String, AtomicLong> windowStartMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> sentCountMap = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 1000;

    @Override
    public String getProviderType() {
        return "SMTP";
    }

    @Override
    public void sendEmail(
            @org.springframework.lang.NonNull String to,
            @org.springframework.lang.NonNull String subject,
            @org.springframework.lang.NonNull String htmlBody,
            Map<String, String> metadata,
            SmtpProvider config) throws ProviderDispatchException {
        // Throttling: use maxSendRate from config
        int maxSendRate = config.getMaxSendRate() != null ? config.getMaxSendRate() : 0;
        String throttleKey = config.getId();
        if (maxSendRate > 0) {
            long now = System.currentTimeMillis();
            windowStartMap.putIfAbsent(throttleKey, new AtomicLong(now));
            sentCountMap.putIfAbsent(throttleKey, new AtomicInteger(0));
            long windowStart = windowStartMap.get(throttleKey).get();
            int sent = sentCountMap.get(throttleKey).get();
            if (now - windowStart > WINDOW_MS) {
                windowStartMap.get(throttleKey).set(now);
                sentCountMap.get(throttleKey).set(0);
            } else if (sent >= maxSendRate) {
                throw new ProviderDispatchException("Throttled: max_send_rate exceeded", false);
            }
            sentCountMap.get(throttleKey).incrementAndGet();
        }
        try {
            // In a real implementation, we would create a new JavaMailSender with config details
            // For now, we continue using the default one but we could easily switch here.
            
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                message.addHeader("X-Legent-" + entry.getKey(), entry.getValue());
            }
            javaMailSender.send(message);
            log.debug("Successfully sent via SMTP to {}", to);
        } catch (Exception e) {
            // Improved bounce classification
            String msg = e.getMessage() != null ? e.getMessage() : "";
            boolean permanent = false;
            if (msg.contains("550") || msg.toLowerCase().contains("mailbox unavailable") || msg.toLowerCase().contains("user unknown") || msg.toLowerCase().contains("hard bounce")) {
                permanent = true;
            } else if (msg.contains("421") || msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("soft bounce") || msg.toLowerCase().contains("rate limit")) {
                permanent = false;
            }
            throw new ProviderDispatchException("SMTP Dispatch failed: " + msg, permanent, e);
        }
    }
}
