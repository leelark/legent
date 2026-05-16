package com.legent.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.cache.service.CacheService;
import com.legent.delivery.client.ContentServiceClient;
import com.legent.delivery.domain.MessageLog;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

import java.util.Map;
import java.util.UUID;

import java.time.Instant;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.event.DeliveryEventPublisher;
import com.legent.delivery.repository.MessageLogRepository;
import com.legent.security.TenantContext;
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
    private static final Pattern CONTENT_REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9:_-]{1,160}$");
    private static final String RENDERED_CONTENT_REFERENCE_PREFIX = "cr_";
    private static final String CONTENT_UNAVAILABLE_FAILURE_CLASS = "CONTENT_UNAVAILABLE";

    private final ProviderSelectionStrategy providerStrategy;
    private final MessageLogRepository messageLogRepository;
    private final DeliveryEventPublisher eventPublisher;
    private final ContentProcessingService contentProcessingService;
    private final CacheService cacheService;
    private final ContentServiceClient contentServiceClient;
    private final ObjectMapper objectMapper;
    private final InboxSafetyService inboxSafetyService;
    private final SendRateControlService sendRateControlService;
    private final WarmupService warmupService;
    private final RetryPolicyService retryPolicyService;

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private DeliveryOrchestrationService self;

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
            workspaceId = TenantContext.getWorkspaceId();
        }
        if (workspaceId == null) {
            throw new IllegalArgumentException("workspaceId is required for delivery execution");
        }
        String teamId = normalize(payload.get("teamId"));
        String jobId = normalize(payload.get("jobId"));
        String batchId = normalize(payload.get("batchId"));
        String experimentId = normalize(payload.get("experimentId"));
        String variantId = normalize(payload.get("variantId"));
        boolean holdout = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("holdout", "false")));
        BigDecimal costReserved = parseMoney(payload.get("costReserved"));
        String htmlBody = normalize(payload.get("htmlBody"));
        if (htmlBody == null) {
            htmlBody = normalize(payload.get("htmlContent"));
        }
        String subject = normalize(payload.get("subject"));
        String fromEmail = normalize(payload.get("fromEmail"));
        String fromName = normalize(payload.get("fromName"));
        String replyToEmail = normalize(payload.get("replyToEmail"));
        String contentReference = normalizeContentReference(payload.get("contentReference"));
        // messageId could be passed or generated.
        // For idempotency we prefer payload messageId, then eventId, and finally generate a UUID.
        String messageId = normalize(payload.get("messageId"));
        if (messageId == null) {
            messageId = normalize((Object) eventId);
        }
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
        }
        if (htmlBody == null && contentReference != null) {
            Map<String, String> referencedContent = fetchContentForRetry(
                    normalizedTenantId,
                    workspaceId,
                    campaignId,
                    messageId,
                    contentReference);
            if (!referencedContent.isEmpty()) {
                if (subject == null) {
                    subject = normalize(referencedContent.get("subject"));
                }
                htmlBody = normalize(referencedContent.get("htmlBody"));
            }
        }
        if (htmlBody == null) {
            htmlBody = "<html><body>Email content</body></html>";
        }
        if (subject == null) {
            subject = "Legent Campaign";
        }

        String previousTenant = TenantContext.getTenantId();
        String previousWorkspace = TenantContext.getWorkspaceId();
        String previousRequest = TenantContext.getRequestId();
        TenantContext.setTenantId(normalizedTenantId);
        TenantContext.setWorkspaceId(workspaceId);
        if (eventId != null && !eventId.isBlank()) {
            TenantContext.setRequestId(eventId);
        }

        // Idempotency check + race-safe create
        Optional<MessageLog> existingLog = messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(normalizedTenantId, workspaceId, messageId);
        if (existingLog.isPresent()
                && !MessageLog.DeliveryStatus.PENDING.name().equals(existingLog.get().getStatus())) {
            log.warn("Message {} already processed with status {}", messageId, existingLog.get().getStatus());
            restoreContext(previousTenant, previousWorkspace, previousRequest);
            return;
        }

        MessageLog logEntry = existingLog.orElse(null);
        if (logEntry == null) {
            MessageLog newLog = new MessageLog();
            newLog.setTenantId(normalizedTenantId);
            newLog.setMessageId(messageId);
            newLog.setCampaignId(campaignId);
            newLog.setWorkspaceId(workspaceId);
            newLog.setTeamId(teamId);
            newLog.setOwnershipScope("WORKSPACE");
            newLog.setJobId(jobId);
            newLog.setBatchId(batchId);
            newLog.setExperimentId(experimentId);
            newLog.setVariantId(variantId);
            newLog.setHoldout(holdout);
            newLog.setCostReserved(costReserved);
            newLog.setSubscriberId(subscriberId);
            newLog.setEmail(email);
            newLog.setFromEmail(fromEmail);
            newLog.setFromName(fromName);
            newLog.setReplyToEmail(replyToEmail);
            // Fix 31: Don't store full HTML body in message_logs - only store references
            // Content is fetched from content-service at retry time if needed
            newLog.setContentReference(contentReference != null ? contentReference : generateContentReference(campaignId, messageId));
            newLog.setAttemptCount(0);
            try {
                logEntry = messageLogRepository.saveAndFlush(newLog);
            } catch (DataIntegrityViolationException duplicateInsert) {
                Optional<MessageLog> winner = messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(normalizedTenantId, workspaceId, messageId);
                if (winner.isEmpty()) {
                    throw duplicateInsert;
                }
                logEntry = winner.get();
                if (!MessageLog.DeliveryStatus.PENDING.name().equals(logEntry.getStatus())) {
                    log.info("Message {} claimed by concurrent worker with status {}", messageId, logEntry.getStatus());
                    restoreContext(previousTenant, previousWorkspace, previousRequest);
                    return;
                }
            }
        }
        if (logEntry.getStatus() == null || logEntry.getStatus().isBlank()) {
            logEntry.setStatus(MessageLog.DeliveryStatus.PENDING.name());
        }
        if (MessageLog.DeliveryStatus.PROCESSING.name().equals(logEntry.getStatus())) {
            log.info("Message {} already being processed", messageId);
            restoreContext(previousTenant, previousWorkspace, previousRequest);
            return;
        }
        int claimed = messageLogRepository.claimForProcessing(
                logEntry.getId(),
                MessageLog.DeliveryStatus.PENDING.name(),
                MessageLog.DeliveryStatus.PROCESSING.name());
        if (claimed == 0) {
            log.info("Message {} already claimed by another worker", messageId);
            restoreContext(previousTenant, previousWorkspace, previousRequest);
            return;
        }
        logEntry.setStatus(MessageLog.DeliveryStatus.PROCESSING.name());
        if (logEntry.getWorkspaceId() == null || logEntry.getWorkspaceId().isBlank()) {
            logEntry.setWorkspaceId(workspaceId);
        }
        if (logEntry.getOwnershipScope() == null || logEntry.getOwnershipScope().isBlank()) {
            logEntry.setOwnershipScope("WORKSPACE");
        }
        if (logEntry.getTeamId() == null || logEntry.getTeamId().isBlank()) {
            logEntry.setTeamId(teamId);
        }
        if (logEntry.getJobId() == null || logEntry.getJobId().isBlank()) {
            logEntry.setJobId(jobId);
        }
        if (logEntry.getBatchId() == null || logEntry.getBatchId().isBlank()) {
            logEntry.setBatchId(batchId);
        }
        if (logEntry.getExperimentId() == null || logEntry.getExperimentId().isBlank()) {
            logEntry.setExperimentId(experimentId);
        }
        if (logEntry.getVariantId() == null || logEntry.getVariantId().isBlank()) {
            logEntry.setVariantId(variantId);
        }
        logEntry.setHoldout(holdout);
        if (logEntry.getCostReserved() == null || logEntry.getCostReserved().compareTo(BigDecimal.ZERO) == 0) {
            logEntry.setCostReserved(costReserved);
        }
        if (logEntry.getFromEmail() == null || logEntry.getFromEmail().isBlank()) {
            logEntry.setFromEmail(fromEmail);
        }
        if (logEntry.getFromName() == null || logEntry.getFromName().isBlank()) {
            logEntry.setFromName(fromName);
        }
        if (logEntry.getReplyToEmail() == null || logEntry.getReplyToEmail().isBlank()) {
            logEntry.setReplyToEmail(replyToEmail);
        }
        if ((logEntry.getContentReference() == null || logEntry.getContentReference().isBlank()) && contentReference != null) {
            logEntry.setContentReference(contentReference);
        }
        // Note: For retries, content is re-fetched from the original event or content-service
        // This prevents 20-100GB/day storage growth from storing full HTML bodies

        String activeRateReservationId = null;
        boolean rateReservationHeld = false;
        boolean providerDispatchAccepted = false;

        try {
            String recipientDomain = extractDomain(email);
            String senderDomain = extractDomain(fromEmail);
            if (recipientDomain == null) {
                throw new ProviderDispatchException("Invalid recipient email", true);
            }
            if (senderDomain == null) {
                senderDomain = recipientDomain;
            }

            InboxSafetyService.InboxSafetyResult safety = inboxSafetyService.evaluate(new InboxSafetyService.InboxSafetyRequest(
                    normalizedTenantId,
                    workspaceId,
                    campaignId,
                    jobId,
                    batchId,
                    messageId,
                    subscriberId,
                    email,
                    fromEmail,
                    senderDomain,
                    null,
                    subject,
                    htmlBody,
                    parseInteger(payload.get("engagementScore")),
                    logEntry.getAttemptCount(),
                    null,
                    null));
            applySafetyResult(logEntry, safety, null, null);
            if (safety.decision() == InboxSafetyService.SafetyDecision.BLOCK) {
                blockUnsafeSend(logEntry, normalizedTenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, safety);
                return;
            }
            if (safety.decision() == InboxSafetyService.SafetyDecision.DEFER) {
                deferUnsafeSend(logEntry, normalizedTenantId, workspaceId, messageId, safety, Instant.now().plus(30, ChronoUnit.MINUTES));
                return;
            }

            // Select provider
            var strategyResult = providerStrategy.selectProvider(normalizedTenantId, senderDomain);
            ProviderAdapter adapter = strategyResult.adapter();
            logEntry.setProviderId(strategyResult.dbRecord().getId());

            SendRateControlService.RateLimitDecision rateLimit = sendRateControlService.reserve(
                    normalizedTenantId,
                    workspaceId,
                    senderDomain,
                    strategyResult.dbRecord().getId(),
                    recipientDomain,
                    strategyResult.dbRecord().getMaxSendRate(),
                    safety.riskScore(),
                    messageId);
            activeRateReservationId = rateLimit.reservationId();
            String warmupStage = rateLimit.warmupStage();
            applySafetyResult(logEntry, safety, rateLimit.rateLimitKey(), warmupStage);
            if (!rateLimit.allowed()) {
                deferUnsafeSend(logEntry, normalizedTenantId, workspaceId, messageId, safety,
                        rateLimit.retryAfter() != null ? rateLimit.retryAfter() : Instant.now().plus(1, ChronoUnit.MINUTES));
                return;
            }
            rateReservationHeld = true;

            int previousAttempts = logEntry.getAttemptCount() != null ? logEntry.getAttemptCount() : 0;
            logEntry.setAttemptCount(previousAttempts + 1);
            
            // Build metadata headers
            Map<String, String> metadata = new java.util.HashMap<>();
            metadata.put("Message-Id", messageId);
            metadata.put("Campaign-Id", campaignId != null ? campaignId : "none");
            metadata.put("Tenant-Id", normalizedTenantId);
            if (fromEmail != null) {
                metadata.put("From-Email", fromEmail);
            }
            if (fromName != null) {
                metadata.put("From-Name", fromName);
            }
            if (replyToEmail != null) {
                metadata.put("Reply-To", replyToEmail);
            }

            // Process content for tracking
            String processedHtml = contentProcessingService.processContent(
                    htmlBody, normalizedTenantId, campaignId, subscriberId, messageId, workspaceId,
                    experimentId, variantId, holdout);

            // Dispatch
            adapter.sendEmail(java.util.Objects.requireNonNull(email), java.util.Objects.requireNonNull(subject), java.util.Objects.requireNonNull(processedHtml), metadata, strategyResult.dbRecord());
            providerDispatchAccepted = true;

            // Fix 31: Clear sensitive/large content from memory after sending
            // (content is not persisted in message_logs to save storage)

            // Success - record with circuit breaker
            providerStrategy.recordProviderSuccess(strategyResult.dbRecord().getId());
            sendRateControlService.settle(normalizedTenantId, workspaceId, activeRateReservationId);
            rateReservationHeld = false;
            logEntry.setStatus(MessageLog.DeliveryStatus.SENT.name());
            logEntry.setProviderResponse("250 OK");
            logEntry.setNextRetryAt(null);

            eventPublisher.publishEmailSent(normalizedTenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId,
                    safetyMetadata(logEntry));

        } catch (ProviderDispatchException e) {
            releaseRateReservationIfNeeded(normalizedTenantId, workspaceId, activeRateReservationId, rateReservationHeld, providerDispatchAccepted, e.getMessage());
            // Record failure with circuit breaker for non-transient failures
            if (e.isPermanent() && logEntry.getProviderId() != null) {
                providerStrategy.recordProviderFailure(logEntry.getProviderId());
            }
            recordWarmupFailure(normalizedTenantId, workspaceId, logEntry, e);
            handleDispatchFailure(logEntry, normalizedTenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, e);
        } catch (Exception e) {
            releaseRateReservationIfNeeded(normalizedTenantId, workspaceId, activeRateReservationId, rateReservationHeld, providerDispatchAccepted, e.getMessage());
            // General hard failure - record with circuit breaker
            if (logEntry.getProviderId() != null) {
                providerStrategy.recordProviderFailure(logEntry.getProviderId());
            }
            recordWarmupFailure(normalizedTenantId, workspaceId, logEntry, e);
            String reason = e.getMessage() != null ? e.getMessage() : "Unexpected delivery failure";
            handleDispatchFailure(logEntry, normalizedTenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId,
                    new ProviderDispatchException(reason, true, e));
        } finally {
            messageLogRepository.save(logEntry);
            restoreContext(previousTenant, previousWorkspace, previousRequest);
        }
    }

    private void releaseRateReservationIfNeeded(String tenantId,
                                                String workspaceId,
                                                String reservationId,
                                                boolean reservationHeld,
                                                boolean providerDispatchAccepted,
                                                String reason) {
        if (!reservationHeld || providerDispatchAccepted || reservationId == null || reservationId.isBlank()) {
            return;
        }
        try {
            sendRateControlService.release(
                    tenantId,
                    workspaceId,
                    reservationId,
                    reason == null || reason.isBlank() ? "PROVIDER_SEND_FAILED" : "PROVIDER_SEND_FAILED: " + reason);
        } catch (Exception releaseFailure) {
            log.error("Failed to release delivery rate reservation {}", reservationId, releaseFailure);
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
        RetryPolicyService.RetryDecision retryDecision = retryPolicyService.decide(e, logEntry.getAttemptCount() != null ? logEntry.getAttemptCount() : 0);
        logEntry.setFailureClass(retryDecision.failureClass());
        int attempts = logEntry.getAttemptCount() != null ? logEntry.getAttemptCount() : 0;

        if (!retryDecision.shouldRetry()) {
            logEntry.setStatus(MessageLog.DeliveryStatus.FAILED.name());
            logEntry.setNextRetryAt(null);
            eventPublisher.publishEmailFailed(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, e.getMessage(),
                    safetyMetadata(logEntry));
            
            if (e.isPermanent()) {
                // Synthesize a bounce event for audience/suppression
                String senderDomain = extractDomain(logEntry.getEmail());
                eventPublisher.publishEmailBounced(tenantId, workspaceId, logEntry.getEmail(), e.getMessage(), senderDomain,
                        safetyMetadata(logEntry));
            }
        } else {
            logEntry.setStatus(MessageLog.DeliveryStatus.PENDING.name());
            Instant nextRetry = retryDecision.nextRetryAt();
            logEntry.setNextRetryAt(nextRetry);
            
            eventPublisher.publishRetryScheduled(tenantId, workspaceId, messageId, attempts, nextRetry.toString(), safetyMetadata(logEntry));
        }
    }

    private void applySafetyResult(MessageLog logEntry,
                                   InboxSafetyService.InboxSafetyResult safety,
                                   String rateLimitKey,
                                   String warmupStage) {
        logEntry.setSafetyDecision(safety.decision().name());
        logEntry.setRiskScore(safety.riskScore());
        if (rateLimitKey != null && !rateLimitKey.isBlank()) {
            logEntry.setRateLimitKey(rateLimitKey);
        }
        if (warmupStage != null && !warmupStage.isBlank()) {
            logEntry.setWarmupStage(warmupStage);
        }
        if (!safety.reasonCodes().isEmpty()) {
            logEntry.setSuppressionReason(String.join(",", safety.reasonCodes()));
        }
    }

    private void blockUnsafeSend(MessageLog logEntry,
                                 String tenantId,
                                 String workspaceId,
                                 String messageId,
                                 String campaignId,
                                 String jobId,
                                 String batchId,
                                 String subscriberId,
                                 InboxSafetyService.InboxSafetyResult safety) {
        String reason = "Inbox safety blocked: " + String.join(",", safety.reasonCodes());
        log.warn("{} for message {}", reason, messageId);
        logEntry.setStatus(MessageLog.DeliveryStatus.FAILED.name());
        logEntry.setFailureClass(resolveSafetyFailureClass(safety));
        logEntry.setProviderResponse(reason);
        logEntry.setNextRetryAt(null);
        eventPublisher.publishEmailFailed(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, reason,
                safetyMetadata(logEntry));
    }

    private void deferUnsafeSend(MessageLog logEntry,
                                 String tenantId,
                                 String workspaceId,
                                 String messageId,
                                 InboxSafetyService.InboxSafetyResult safety,
                                 Instant retryAt) {
        String reason = "Inbox safety deferred: " + String.join(",", safety.reasonCodes());
        log.warn("{} for message {}", reason, messageId);
        logEntry.setStatus(MessageLog.DeliveryStatus.PENDING.name());
        logEntry.setFailureClass("REPUTATION_DEFERRED");
        logEntry.setProviderResponse(reason);
        logEntry.setNextRetryAt(retryAt);
        eventPublisher.publishRetryScheduled(tenantId, workspaceId, messageId,
                logEntry.getAttemptCount() != null ? logEntry.getAttemptCount() : 0,
                retryAt.toString(), safetyMetadata(logEntry));
    }

    private String resolveSafetyFailureClass(InboxSafetyService.InboxSafetyResult safety) {
        if (safety.reasonCodes().contains("RECIPIENT_SUPPRESSED")) {
            return "SUPPRESSED";
        }
        if (safety.reasonCodes().contains("CONTENT_PLACEHOLDER") || safety.reasonCodes().contains("SPAM_PATTERN")) {
            return "CONTENT_BLOCKED";
        }
        if (safety.reasonCodes().contains("MISSING_UNSUBSCRIBE") || safety.reasonCodes().contains("AUTH_OR_COMPLIANCE_BLOCK")) {
            return "COMPLIANCE_BLOCKED";
        }
        return "REPUTATION_BLOCKED";
    }

    private Map<String, String> safetyMetadata(MessageLog logEntry) {
        Map<String, String> metadata = new java.util.HashMap<>();
        if (logEntry.getSafetyDecision() != null) {
            metadata.put("safetyDecision", logEntry.getSafetyDecision());
        }
        if (logEntry.getRiskScore() != null) {
            metadata.put("riskScore", String.valueOf(logEntry.getRiskScore()));
        }
        if (logEntry.getProviderScore() != null) {
            metadata.put("providerScore", String.valueOf(logEntry.getProviderScore()));
        }
        if (logEntry.getRateLimitKey() != null) {
            metadata.put("rateLimitKey", logEntry.getRateLimitKey());
        }
        if (logEntry.getWarmupStage() != null) {
            metadata.put("warmupStage", logEntry.getWarmupStage());
        }
        if (logEntry.getFailureClass() != null) {
            metadata.put("failureClass", logEntry.getFailureClass());
        }
        if (logEntry.getSuppressionReason() != null) {
            metadata.put("suppressionReason", logEntry.getSuppressionReason());
        }
        if (logEntry.getExperimentId() != null) {
            metadata.put("experimentId", logEntry.getExperimentId());
        }
        if (logEntry.getVariantId() != null) {
            metadata.put("variantId", logEntry.getVariantId());
        }
        metadata.put("holdout", String.valueOf(logEntry.isHoldout()));
        if (logEntry.getCostReserved() != null) {
            metadata.put("costReserved", logEntry.getCostReserved().toPlainString());
        }
        return metadata;
    }

    private void recordWarmupFailure(String tenantId, String workspaceId, MessageLog logEntry, Exception exception) {
        if (logEntry.getProviderId() == null) {
            return;
        }
        String senderDomain = extractDomain(logEntry.getFromEmail());
        if (senderDomain == null) {
            senderDomain = extractDomain(logEntry.getEmail());
        }
        if (senderDomain == null) {
            return;
        }
        ProviderDispatchException dispatchException = exception instanceof ProviderDispatchException pde
                ? pde
                : new ProviderDispatchException(exception.getMessage(), true, exception);
        warmupService.recordNegativeSignal(tenantId, workspaceId, senderDomain, logEntry.getProviderId(),
                retryPolicyService.classify(dispatchException));
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal parseMoney(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60000)
    public void processScheduledRetries() {
        java.util.List<MessageLog> retries = messageLogRepository.findEligibleForRetry(Instant.now());
        for (MessageLog logEntry : retries) {
            try {
                if (normalize(logEntry.getEmail()) == null || normalize(logEntry.getMessageId()) == null) {
                    log.warn("Skipping scheduled retry for malformed message log {}", logEntry.getId());
                    continue;
                }
                if (normalize(logEntry.getWorkspaceId()) == null) {
                    log.warn("Skipping scheduled retry for message {} due to missing workspace", logEntry.getMessageId());
                    continue;
                }

                // Fix 31: Fetch content from content-service instead of message_logs
                // (message_logs no longer stores full HTML to prevent 20-100GB/day storage growth)
                Map<String, String> content;
                try {
                    content = fetchContentForRetry(
                            logEntry.getTenantId(),
                            logEntry.getWorkspaceId(),
                            logEntry.getCampaignId(),
                            logEntry.getMessageId(),
                            logEntry.getContentReference());
                } catch (IllegalStateException e) {
                    failRetryForMissingContent(logEntry, e.getMessage());
                    continue;
                }
                if (!hasResolvedRetryContent(content)) {
                    failRetryForMissingContent(logEntry, "Retry content is missing required subject/htmlBody");
                    continue;
                }

                // Build complete payload with all required fields for retry
                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("email", logEntry.getEmail());
                payload.put("subscriberId", logEntry.getSubscriberId() != null ? logEntry.getSubscriberId() : "");
                payload.put("campaignId", logEntry.getCampaignId() != null ? logEntry.getCampaignId() : "");
                payload.put("workspaceId", logEntry.getWorkspaceId());
                payload.put("jobId", logEntry.getJobId() != null ? logEntry.getJobId() : "");
                payload.put("batchId", logEntry.getBatchId() != null ? logEntry.getBatchId() : "");
                payload.put("experimentId", logEntry.getExperimentId() != null ? logEntry.getExperimentId() : "");
                payload.put("variantId", logEntry.getVariantId() != null ? logEntry.getVariantId() : "");
                payload.put("holdout", logEntry.isHoldout());
                payload.put("costReserved", logEntry.getCostReserved() != null ? logEntry.getCostReserved() : BigDecimal.ZERO);
                payload.put("messageId", logEntry.getMessageId());
                payload.put("contentReference", logEntry.getContentReference());
                payload.put("fromEmail", logEntry.getFromEmail() != null ? logEntry.getFromEmail() : "");
                payload.put("fromName", logEntry.getFromName() != null ? logEntry.getFromName() : "");
                payload.put("replyToEmail", logEntry.getReplyToEmail() != null ? logEntry.getReplyToEmail() : "");
                payload.put("subject", content.get("subject"));
                payload.put("htmlBody", content.get("htmlBody"));
                // Call through self proxy to ensure @Transactional boundary is applied.
                DeliveryOrchestrationService delegate = self != null ? self : this;
                delegate.processSendRequest(payload, logEntry.getTenantId(), logEntry.getMessageId());
            } catch (Exception e) {
                log.warn("Scheduled retry loop failure", e);
            }
        }
    }

    private boolean hasResolvedRetryContent(Map<String, String> content) {
        return content != null
                && normalize(content.get("subject")) != null
                && normalize(content.get("htmlBody")) != null;
    }

    private void failRetryForMissingContent(MessageLog logEntry, String reason) {
        String failureReason = normalize(reason);
        if (failureReason == null) {
            failureReason = "Retry content is unavailable";
        }
        log.warn("Failing retry for message {}: {}", logEntry.getMessageId(), failureReason);
        logEntry.setStatus(MessageLog.DeliveryStatus.FAILED.name());
        logEntry.setFailureClass(CONTENT_UNAVAILABLE_FAILURE_CLASS);
        logEntry.setProviderResponse(failureReason);
        logEntry.setNextRetryAt(null);
        messageLogRepository.save(logEntry);
        eventPublisher.publishEmailFailed(
                logEntry.getTenantId(),
                logEntry.getWorkspaceId(),
                logEntry.getMessageId(),
                logEntry.getCampaignId(),
                logEntry.getJobId(),
                logEntry.getBatchId(),
                logEntry.getSubscriberId(),
                failureReason,
                safetyMetadata(logEntry));
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

    private String normalizeContentReference(Object value) {
        String contentReference = normalize(value);
        if (contentReference == null) {
            return null;
        }
        if (!CONTENT_REFERENCE_PATTERN.matcher(contentReference).matches()) {
            throw new IllegalArgumentException("contentReference contains unsupported characters");
        }
        return contentReference;
    }

    private void restoreContext(String tenantId, String workspaceId, String requestId) {
        TenantContext.clear();
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        TenantContext.setTenantId(tenantId);
        if (workspaceId != null && !workspaceId.isBlank()) {
            TenantContext.setWorkspaceId(workspaceId);
        }
        if (requestId != null && !requestId.isBlank()) {
            TenantContext.setRequestId(requestId);
        }
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
    private Map<String, String> fetchContentForRetry(String tenantId, String workspaceId, String campaignId, String messageId, String contentReference) {
        Map<String, String> result = new java.util.HashMap<>();
        String normalizedReference = normalizeContentReference(contentReference);

        // Try to fetch from cache first (content may be cached for 24-48 hours)
        if (normalizedReference != null) {
            try {
                // Attempt to get from Redis cache using contentReference as key
                Optional<String> cached = cacheService.get("email:content:" + normalizedReference, String.class);
                if (cached.isPresent()) {
                    // LEGENT-MED-001: Use injected ObjectMapper instead of creating new instance
                    Map<String, String> cachedContent = objectMapper.readValue(cached.get(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
                    validateReferencedContent(cachedContent, tenantId, workspaceId, campaignId, messageId, normalizedReference);
                    return cachedContent;
                }
            } catch (Exception e) {
                log.debug("Failed to fetch cached content for retry: {}", e.getMessage());
            }
            if (normalizedReference.startsWith(RENDERED_CONTENT_REFERENCE_PREFIX)) {
                throw new IllegalStateException("Rendered content reference is unavailable for retry: " + normalizedReference);
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
        log.error("Content not found for retry - campaignId={}, reference={}. Retry will not use placeholder content.",
                campaignId, normalizedReference);
        return result;
    }

    private void validateReferencedContent(Map<String, String> content,
                                           String tenantId,
                                           String workspaceId,
                                           String campaignId,
                                           String messageId,
                                           String contentReference) {
        if (normalize(content.get("subject")) == null || normalize(content.get("htmlBody")) == null) {
            throw new IllegalStateException("Rendered content reference is missing required content fields");
        }
        requireMatchingField(content, "tenantId", tenantId, contentReference);
        requireMatchingField(content, "workspaceId", workspaceId, contentReference);
        requireMatchingField(content, "campaignId", campaignId, contentReference);
        requireMatchingField(content, "messageId", messageId, contentReference);
    }

    private void requireMatchingField(Map<String, String> content, String field, String expected, String contentReference) {
        String actual = normalize(content.get(field));
        String normalizedExpected = normalize(expected);
        if (actual != null && normalizedExpected != null && !actual.equals(normalizedExpected)) {
            throw new IllegalStateException("Rendered content reference " + contentReference + " does not match " + field);
        }
    }

}
