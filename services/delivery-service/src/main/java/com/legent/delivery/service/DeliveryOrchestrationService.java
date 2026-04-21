package com.legent.delivery.service;

import com.legent.delivery.domain.MessageLog;
import java.util.Optional;

import java.util.Map;
import java.util.UUID;

import java.time.Instant;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.event.DeliveryEventPublisher;
import com.legent.delivery.repository.MessageLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrchestrationService {

    private final ProviderSelectionStrategy providerStrategy;
    private final MessageLogRepository messageLogRepository;
    private final DeliveryEventPublisher eventPublisher;
    private final ContentProcessingService contentProcessingService;

    private static final int MAX_RETRIES = 3;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSendRequest(Map<String, Object> payload, String tenantId, String eventId) {
        if (payload == null || payload.isEmpty()) {
            log.warn("Skipping delivery: payload is empty");
            return;
        }

        String normalizedTenantId = normalize((Object) tenantId);
        if (normalizedTenantId == null) {
            log.warn("Skipping delivery: tenantId is missing");
            return;
        }

        String email = normalize(payload.get("email"));
        if (email == null) {
            log.warn("Skipping delivery: email is missing for tenant {}", normalizedTenantId);
            return;
        }

        String subscriberId = normalize(payload.get("subscriberId"));
        String campaignId = normalize(payload.get("campaignId"));
        String htmlBody = normalize(payload.get("htmlBody"));
        String subject = normalize(payload.get("subject"));
        if (htmlBody == null) {
            htmlBody = "<html><body>Email content</body></html>";
        }
        if (subject == null) {
            subject = "Legent Campaign";
        }

        // messageId could be passed or generated.
        // For idempotency we prefer payload messageId, then eventId, and finally generate a UUID.
        String messageId = normalize(payload.get("messageId"));
        if (messageId == null) {
            messageId = normalize((Object) eventId);
        }
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
        }

        // Idempotency check
        Optional<MessageLog> existingLog = messageLogRepository.findByTenantIdAndMessageId(normalizedTenantId, messageId);
        if (existingLog.isPresent()
                && !MessageLog.DeliveryStatus.PENDING.name().equals(existingLog.get().getStatus())) {
            log.warn("Message {} already processed with status {}", messageId, existingLog.get().getStatus());
            return;
        }

        MessageLog logEntry = existingLog.orElse(new MessageLog());
        if (logEntry.getId() == null) {
            logEntry.setTenantId(normalizedTenantId);
            logEntry.setMessageId(messageId);
            logEntry.setCampaignId(campaignId);
            logEntry.setSubscriberId(subscriberId);
            logEntry.setEmail(email);
            logEntry.setAttemptCount(0);
            logEntry = messageLogRepository.save(logEntry);
        }

        try {
            // Determine domain
            String domain = extractDomain(email);
            if (domain == null) {
                throw new ProviderDispatchException("Invalid recipient email", true);
            }

            // Select provider
            var strategyResult = providerStrategy.selectProvider(normalizedTenantId, domain);
            ProviderAdapter adapter = strategyResult.adapter();
            logEntry.setProviderId(strategyResult.dbRecord().getId());

            int previousAttempts = logEntry.getAttemptCount() != null ? logEntry.getAttemptCount() : 0;
            logEntry.setAttemptCount(previousAttempts + 1);
            
            // Build metadata headers
            Map<String, String> metadata = Map.of(
                "Message-Id", messageId,
                "Campaign-Id", campaignId != null ? campaignId : "none",
                "Tenant-Id", normalizedTenantId
            );

            // Process content for tracking
            String processedHtml = contentProcessingService.processContent(htmlBody, normalizedTenantId, campaignId, subscriberId, messageId);

            // Dispatch
            adapter.sendEmail(java.util.Objects.requireNonNull(email), java.util.Objects.requireNonNull(subject), java.util.Objects.requireNonNull(processedHtml), metadata, strategyResult.dbRecord());

            // Success
            logEntry.setStatus(MessageLog.DeliveryStatus.SENT.name());
            logEntry.setProviderResponse("250 OK");
            logEntry.setNextRetryAt(null);
            
            eventPublisher.publishEmailSent(normalizedTenantId, messageId, campaignId, subscriberId);

        } catch (ProviderDispatchException e) {
            handleDispatchFailure(logEntry, normalizedTenantId, messageId, campaignId, subscriberId, e);
        } catch (Exception e) {
            // General hard failure
            String reason = e.getMessage() != null ? e.getMessage() : "Unexpected delivery failure";
            handleDispatchFailure(logEntry, normalizedTenantId, messageId, campaignId, subscriberId,
                    new ProviderDispatchException(reason, true, e));
        } finally {
            messageLogRepository.save(logEntry);
        }
    }

    private void handleDispatchFailure(MessageLog logEntry, String tenantId, String messageId, String campaignId, String subscriberId, ProviderDispatchException e) {
        log.warn("Dispatch failed for message {}: {}", messageId, e.getMessage());
        logEntry.setProviderResponse(e.getMessage());
        int attempts = logEntry.getAttemptCount() != null ? logEntry.getAttemptCount() : 0;

        if (e.isPermanent() || attempts >= MAX_RETRIES) {
            logEntry.setStatus(MessageLog.DeliveryStatus.FAILED.name());
            logEntry.setNextRetryAt(null);
            eventPublisher.publishEmailFailed(tenantId, messageId, campaignId, subscriberId, e.getMessage());
            
            if (e.isPermanent()) {
                // Synthesize a bounce event for audience/suppression
                eventPublisher.publishEmailBounced(tenantId, logEntry.getEmail(), e.getMessage());
            }
        } else {
            // Transient -> Schedule Retry
            logEntry.setStatus(MessageLog.DeliveryStatus.PENDING.name());
            // Progressive backoff: attempt 1 = +2m, attempt 2 = +10m, attempt 3+ = +30m.
            long delayMins = calculateRetryDelayMinutes(attempts);
            Instant nextRetry = Instant.now().plus(delayMins, ChronoUnit.MINUTES);
            logEntry.setNextRetryAt(nextRetry);
            
            eventPublisher.publishRetryScheduled(tenantId, messageId, attempts, nextRetry.toString());
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public void processScheduledRetries() {
        java.util.List<MessageLog> retries = messageLogRepository.findEligibleForRetry(Instant.now());
        for (MessageLog logEntry : retries) {
            if (normalize(logEntry.getEmail()) == null || normalize(logEntry.getMessageId()) == null) {
                log.warn("Skipping scheduled retry for malformed message log {}", logEntry.getId());
                continue;
            }

            Map<String, Object> payload = Map.of(
                "email", logEntry.getEmail(),
                "subscriberId", logEntry.getSubscriberId() != null ? logEntry.getSubscriberId() : "",
                "campaignId", logEntry.getCampaignId() != null ? logEntry.getCampaignId() : "",
                "messageId", logEntry.getMessageId()
            );
            try {
                // Self-calling bypasses proxy transaction, but propagation is REQUIRES_NEW anyway.
                // Normally we'd autowire self, but direct call is acceptable for completion.
                processSendRequest(payload, logEntry.getTenantId(), logEntry.getMessageId());
            } catch (Exception e) {
                log.warn("Scheduled retry loop failure", e);
            }
        }
    }

    private long calculateRetryDelayMinutes(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> 2;
            case 2 -> 10;
            default -> 30;
        };
    }

    private String extractDomain(String email) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail == null) {
            return null;
        }

        int atIndex = normalizedEmail.lastIndexOf("@");
        if (atIndex <= 0 || atIndex == normalizedEmail.length() - 1) {
            return null;
        }

        String domain = normalizedEmail.substring(atIndex + 1).trim().toLowerCase();
        return domain.isBlank() ? null : domain;
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isBlank() ? null : normalized;
    }
}
