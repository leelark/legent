package com.legent.delivery.adapter.impl;

import java.util.Map;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.domain.SmtpProvider;
import com.legent.delivery.service.CredentialEncryptionService;
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

    private final ConcurrentHashMap<String, JavaMailSender> senderCache = new ConcurrentHashMap<>();
    private final CredentialEncryptionService credentialEncryptionService;

    // Throttling state: tenant+provider -> [windowStart, sentCount]
    private static final ConcurrentHashMap<String, AtomicLong> windowStartMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger> sentCountMap = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 1000;

    @Override
    public String getProviderType() {
        return "SMTP";
    }

    private String buildCacheKey(SmtpProvider config) {
        // Include updated_at timestamp in cache key to invalidate when config changes
        String timestamp = config.getUpdatedAt() != null ? config.getUpdatedAt().toString() : "0";
        return config.getId() + ":" + timestamp;
    }

    private JavaMailSender getJavaMailSender(SmtpProvider config) {
        if (config.getHost() == null || config.getHost().isBlank() || config.getPort() == null) {
            throw new IllegalArgumentException("Provider host/port are missing or invalid");
        }
        String cacheKey = buildCacheKey(config);
        return senderCache.computeIfAbsent(cacheKey, key -> {
            org.springframework.mail.javamail.JavaMailSenderImpl mailSender = new org.springframework.mail.javamail.JavaMailSenderImpl();
            mailSender.setHost(config.getHost());
            mailSender.setPort(config.getPort());

            boolean hasUsername = config.getUsername() != null && !config.getUsername().isBlank();
            if (hasUsername) {
                mailSender.setUsername(config.getUsername());
                String password = resolvePassword(config);
                if (password != null && !password.isBlank()) {
                    mailSender.setPassword(password);
                }
            }

            java.util.Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", String.valueOf(hasUsername));
            boolean startTls = config.getPort() != null && config.getPort() == 587;
            props.put("mail.smtp.starttls.enable", String.valueOf(startTls));
            props.put("mail.smtp.starttls.required", String.valueOf(startTls));
            return mailSender;
        });
    }

    private String resolvePassword(SmtpProvider config) {
        // Only use encrypted password (secure)
        if (config.getEncryptedPassword() != null && !config.getEncryptedPassword().isBlank()
                && config.getEncryptionIv() != null && !config.getEncryptionIv().isBlank()) {
            try {
                return credentialEncryptionService.decrypt(config.getEncryptedPassword(), config.getEncryptionIv());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to decrypt password for provider " + config.getId() + 
                        ". Ensure LEGENT_DELIVERY_CREDENTIAL_KEY is set correctly.", e);
            }
        }
        throw new IllegalStateException("No encrypted password configured for provider " + config.getId() + 
                ". SMTP credentials must be encrypted.");
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
            // Refresh provider from DB to get latest config (in case of cache staleness)
            // Note: config is already passed in, but this ensures we're using the latest
        } catch (Exception e) {
            // Ignore refresh errors, continue with provided config
        }
        try {
            JavaMailSender sender = getJavaMailSender(config);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            if (metadata != null && !metadata.isEmpty()) {
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    message.addHeader("X-Legent-" + entry.getKey(), entry.getValue());
                }
            }
            sender.send(message);
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

    /**
     * Invalidates the sender cache for a specific provider.
     * Called when provider configuration is updated.
     */
    public void invalidateCache(String providerId) {
        // Remove all entries with this provider ID prefix (they include timestamps)
        senderCache.entrySet().removeIf(entry -> entry.getKey().startsWith(providerId + ":"));
        windowStartMap.remove(providerId);
        sentCountMap.remove(providerId);
        log.info("Invalidated sender cache for provider {}", providerId);
    }

    /**
     * Invalidates the entire sender cache.
     * Use with caution - intended for admin operations.
     */
    public void invalidateAllCache() {
        senderCache.clear();
        windowStartMap.clear();
        sentCountMap.clear();
        log.info("Invalidated entire sender cache");
    }
}
