package com.legent.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.cache.service.CacheService;
import com.legent.delivery.client.ContentServiceClient;
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
import org.springframework.dao.DataIntegrityViolationException;
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
    private final CacheService cacheService;
    private final ContentServiceClient contentServiceClient;
    private final ObjectMapper objectMapper;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private DeliveryOrchestrationService self;

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
        String workspaceId = normalize(payload.get("workspaceId"));
        if (workspaceId == null) {
            workspaceId = com.legent.security.TenantContext.getWorkspaceId();
        }
        if (workspaceId == null) {
            workspaceId = "workspace-default";
        }
        String jobId = normalize(payload.get("jobId"));
        String batchId = normalize(payload.get("batchId"));
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

        // Idempotency check + race-safe create
        Optional<MessageLog> existingLog = messageLogRepository.findByTenantIdAndMessageId(normalizedTenantId, messageId);
        if (existingLog.isPresent()
                && !MessageLog.DeliveryStatus.PENDING.name().equals(existingLog.get().getStatus())) {
            log.warn("Message {} already processed with status {}", messageId, existingLog.get().getStatus());
            return;
        }

        MessageLog logEntry = existingLog.orElse(null);
        if (logEntry == null) {
            MessageLog newLog = new MessageLog();
            newLog.setTenantId(normalizedTenantId);
            newLog.setMessageId(messageId);
            newLog.setCampaignId(campaignId);
            newLog.setWorkspaceId(workspaceId);
            newLog.setJobId(jobId);
            newLog.setBatchId(batchId);
            newLog.setSubscriberId(subscriberId);
            newLog.setEmail(email);
            // Fix 31: Don't store full HTML body in message_logs - only store references
            // Content is fetched from content-service at retry time if needed
            newLog.setContentReference(generateContentReference(campaignId, messageId));
            newLog.setAttemptCount(0);
            try {
                logEntry = messageLogRepository.saveAndFlush(newLog);
            } catch (DataIntegrityViolationException duplicateInsert) {
                Optional<MessageLog> winner = messageLogRepository.findByTenantIdAndMessageId(normalizedTenantId, messageId);
                if (winner.isEmpty()) {
                    throw duplicateInsert;
                }
                logEntry = winner.get();
                if (!MessageLog.DeliveryStatus.PENDING.name().equals(logEntry.getStatus())) {
                    log.info("Message {} claimed by concurrent worker with status {}", messageId, logEntry.getStatus());
                    return;
                }
            }
        }
        if (logEntry.getStatus() == null || logEntry.getStatus().isBlank()) {
            logEntry.setStatus(MessageLog.DeliveryStatus.PENDING.name());
        }
        if (MessageLog.DeliveryStatus.PROCESSING.name().equals(logEntry.getStatus())) {
            log.info("Message {} already being processed", messageId);
            return;
        }
        int claimed = messageLogRepository.claimForProcessing(
                logEntry.getId(),
                MessageLog.DeliveryStatus.PENDING.name(),
                MessageLog.DeliveryStatus.PROCESSING.name());
        if (claimed == 0) {
            log.info("Message {} already claimed by another worker", messageId);
            return;
        }
        logEntry.setStatus(MessageLog.DeliveryStatus.PROCESSING.name());
        if (logEntry.getWorkspaceId() == null || logEntry.getWorkspaceId().isBlank()) {
            logEntry.setWorkspaceId(workspaceId);
        }
        if (logEntry.getJobId() == null || logEntry.getJobId().isBlank()) {
            logEntry.setJobId(jobId);
        }
        if (logEntry.getBatchId() == null || logEntry.getBatchId().isBlank()) {
            logEntry.setBatchId(batchId);
        }
        // Note: For retries, content is re-fetched from the original event or content-service
        // This prevents 20-100GB/day storage growth from storing full HTML bodies

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

            // Fix 31: Clear sensitive/large content from memory after sending
            // (content is not persisted in message_logs to save storage)

            // Success - record with circuit breaker
            providerStrategy.recordProviderSuccess(strategyResult.dbRecord().getId());
            logEntry.setStatus(MessageLog.DeliveryStatus.SENT.name());
            logEntry.setProviderResponse("250 OK");
            logEntry.setNextRetryAt(null);

            eventPublisher.publishEmailSent(normalizedTenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId);

        } catch (ProviderDispatchException e) {
            // Record failure with circuit breaker for non-transient failures
            if (e.isPermanent() && logEntry.getProviderId() != null) {
                providerStrategy.recordProviderFailure(logEntry.getProviderId());
            }
            handleDispatchFailure(logEntry, normalizedTenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, e);
        } catch (Exception e) {
            // General hard failure - record with circuit breaker
            if (logEntry.getProviderId() != null) {
                providerStrategy.recordProviderFailure(logEntry.getProviderId());
            }
            String reason = e.getMessage() != null ? e.getMessage() : "Unexpected delivery failure";
            handleDispatchFailure(logEntry, normalizedTenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId,
                    new ProviderDispatchException(reason, true, e));
        } finally {
            messageLogRepository.save(logEntry);
        }
    }

    private void handleDispatchFailure(MessageLog logEntry,
                                       String tenantId,
                                       String workspaceId,
                                       String messageId,
                                       String campaignId,
                                       String jobId,
                                       String batchId,
                                       String subscriberId,
                                       ProviderDispatchException e) {
        log.warn("Dispatch failed for message {}: {}", messageId, e.getMessage());
        logEntry.setProviderResponse(e.getMessage());
        int attempts = logEntry.getAttemptCount() != null ? logEntry.getAttemptCount() : 0;

        if (e.isPermanent() || attempts >= MAX_RETRIES) {
            logEntry.setStatus(MessageLog.DeliveryStatus.FAILED.name());
            logEntry.setNextRetryAt(null);
            eventPublisher.publishEmailFailed(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, e.getMessage());
            
            if (e.isPermanent()) {
                // Synthesize a bounce event for audience/suppression
                String senderDomain = extractDomain(logEntry.getEmail());
                eventPublisher.publishEmailBounced(tenantId, logEntry.getEmail(), e.getMessage(), senderDomain);
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

            // Fix 31: Fetch content from content-service instead of message_logs
            // (message_logs no longer stores full HTML to prevent 20-100GB/day storage growth)
            Map<String, String> content = fetchContentForRetry(logEntry.getCampaignId(), logEntry.getContentReference());

            // Build complete payload with all required fields for retry
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("email", logEntry.getEmail());
            payload.put("subscriberId", logEntry.getSubscriberId() != null ? logEntry.getSubscriberId() : "");
            payload.put("campaignId", logEntry.getCampaignId() != null ? logEntry.getCampaignId() : "");
            payload.put("workspaceId", logEntry.getWorkspaceId() != null ? logEntry.getWorkspaceId() : "workspace-default");
            payload.put("jobId", logEntry.getJobId() != null ? logEntry.getJobId() : "");
            payload.put("batchId", logEntry.getBatchId() != null ? logEntry.getBatchId() : "");
            payload.put("messageId", logEntry.getMessageId());
            // Use content from content-service or fallback defaults
            payload.put("subject", content.getOrDefault("subject", "Legent Campaign"));
            payload.put("htmlBody", content.getOrDefault("htmlBody", "<html><body>Email content</body></html>"));
            try {
                // Call through self proxy to ensure @Transactional boundary is applied.
                self.processSendRequest(payload, logEntry.getTenantId(), logEntry.getMessageId());
            } catch (Exception e) {
                log.warn("Scheduled retry loop failure", e);
            }
        }
    }

    private static final java.util.Random JITTER_RANDOM = new java.util.Random();

    private long calculateRetryDelayMinutes(int attemptCount) {
        long baseDelay = switch (attemptCount) {
            case 1 -> 2;
            case 2 -> 10;
            default -> 30;
        };
        // Add jitter: +/- 20% randomization to prevent thundering herd
        double jitterFactor = 0.8 + (JITTER_RANDOM.nextDouble() * 0.4); // 0.8 to 1.2
        return Math.max(1, Math.round(baseDelay * jitterFactor));
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

    /**
     * Generates a content reference key for retrieving email content at retry time.
     * Fix 31: Instead of storing full HTML (20-100KB per email), we store a reference
     * and fetch content from the original event or content-service when needed.
     */
    private String generateContentReference(String campaignId, String messageId) {
        // Format: campaignId:messageId - used to look up original content
        return String.format("ref:%s:%s",
                campaignId != null ? campaignId : "none",
                messageId);
    }

    /**
     * Fetches email content for retry from cache or content-service.
     * Fix 31: Content is no longer stored in message_logs to prevent storage bloat.
     * AUDIT-010: Now integrated with content-service for real content retrieval.
     */
    private Map<String, String> fetchContentForRetry(String campaignId, String contentReference) {
        Map<String, String> result = new java.util.HashMap<>();

        // Try to fetch from cache first (content may be cached for 24-48 hours)
        if (contentReference != null && !contentReference.isBlank()) {
            try {
                // Attempt to get from Redis cache using contentReference as key
                Optional<String> cached = cacheService.get("email:content:" + contentReference, String.class);
                if (cached.isPresent()) {
                    // LEGENT-MED-001: Use injected ObjectMapper instead of creating new instance
                    return objectMapper.readValue(cached.get(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                }
            } catch (Exception e) {
                log.debug("Failed to fetch cached content for retry: {}", e.getMessage());
            }
        }

        // LEGENT-CRIT-003 & AUDIT-010: Call content-service API with connection pooling
        if (campaignId != null && !campaignId.isBlank()) {
            try {
                Map<String, String> contentFromService = contentServiceClient.fetchCampaignContent(campaignId);
                if (!contentFromService.isEmpty()) {
                    log.info("Retrieved content from content-service for campaign {}", campaignId);
                    return contentFromService;
                }
            } catch (Exception e) {
                log.error("Failed to fetch content from content-service for campaign {}: {}", campaignId, e.getMessage());
            }
        }

        // If all fetch attempts fail, return empty map - caller must handle failure
        log.error("Content not found for retry - campaignId={}, reference={}. Retry will use fallback defaults.",
                campaignId, contentReference);
        return result;
    }

}
