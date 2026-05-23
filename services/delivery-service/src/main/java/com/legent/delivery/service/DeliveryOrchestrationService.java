package com.legent.delivery.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legent.cache.service.CacheService;
import com.legent.delivery.client.ContentServiceClient;
import com.legent.delivery.domain.MessageLog;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import java.util.Map;
import java.util.UUID;

import java.time.Instant;

import com.legent.delivery.adapter.ProviderAdapter;
import com.legent.delivery.adapter.ProviderDispatchException;
import com.legent.delivery.repository.MessageLogRepository;
import com.legent.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrchestrationService {
    private static final Pattern CONTENT_REFERENCE_PATTERN = Pattern.compile("^[A-Za-z0-9:_-]{1,160}$");
    private static final Pattern SNAPSHOT_HASH_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final String RENDERED_CONTENT_REFERENCE_PREFIX = "cr_";
    private static final String CONTENT_UNAVAILABLE_FAILURE_CLASS = "CONTENT_UNAVAILABLE";
    private static final String GOVERNANCE_POLICY_FAILURE_CLASS = "SEND_GOVERNANCE_POLICY_BLOCKED";
    private static final int SCHEDULED_RETRY_BATCH_SIZE = 500;

    private final ProviderSelectionStrategy providerStrategy;
    private final MessageLogRepository messageLogRepository;
    private final DeliveryFeedbackOutboxService feedbackOutboxService;
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
        String contentReference = normalize(payload.get("contentReference"));
        String sendGovernancePolicyId = normalize(payload.get("sendGovernancePolicyId"));
        String sendGovernancePolicyKey = normalize(payload.get("sendGovernancePolicyKey"));
        Long sendGovernancePolicyVersion = parseLong(payload.get("sendGovernancePolicyVersion"));
        String sendGovernancePolicySnapshotHash = normalize(payload.get("sendGovernancePolicySnapshotHash"));
        String sendGovernancePolicySnapshot = canonicalSnapshotJson(payload.get("sendGovernancePolicySnapshot"));
        // messageId could be passed or generated.
        // For idempotency we prefer payload messageId, then eventId, and finally generate a UUID.
        String messageId = normalize(payload.get("messageId"));
        if (messageId == null) {
            messageId = normalize((Object) eventId);
        }
        if (messageId == null) {
            messageId = UUID.randomUUID().toString();
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
            newLog.setSendGovernancePolicyId(sendGovernancePolicyId);
            newLog.setSendGovernancePolicyKey(sendGovernancePolicyKey);
            newLog.setSendGovernancePolicyVersion(sendGovernancePolicyVersion);
            newLog.setSendGovernancePolicySnapshotHash(sendGovernancePolicySnapshotHash);
            newLog.setSendGovernancePolicySnapshot(sendGovernancePolicySnapshot);
            // Fix 31: Don't store full HTML body in message_logs - only store references
            // Content is fetched from content-service at retry time if needed
            newLog.setContentReference(contentReference);
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
        if ((logEntry.getSendGovernancePolicyId() == null || logEntry.getSendGovernancePolicyId().isBlank()) && sendGovernancePolicyId != null) {
            logEntry.setSendGovernancePolicyId(sendGovernancePolicyId);
        }
        if ((logEntry.getSendGovernancePolicyKey() == null || logEntry.getSendGovernancePolicyKey().isBlank()) && sendGovernancePolicyKey != null) {
            logEntry.setSendGovernancePolicyKey(sendGovernancePolicyKey);
        }
        if (logEntry.getSendGovernancePolicyVersion() == null && sendGovernancePolicyVersion != null) {
            logEntry.setSendGovernancePolicyVersion(sendGovernancePolicyVersion);
        }
        if ((logEntry.getSendGovernancePolicySnapshotHash() == null || logEntry.getSendGovernancePolicySnapshotHash().isBlank()) && sendGovernancePolicySnapshotHash != null) {
            logEntry.setSendGovernancePolicySnapshotHash(sendGovernancePolicySnapshotHash);
        }
        if ((logEntry.getSendGovernancePolicySnapshot() == null || logEntry.getSendGovernancePolicySnapshot().isBlank()) && sendGovernancePolicySnapshot != null) {
            logEntry.setSendGovernancePolicySnapshot(sendGovernancePolicySnapshot);
        }
        if (contentReference == null) {
            contentReference = normalize(logEntry.getContentReference());
        }
        // Note: For retries, content is re-fetched from the original event or content-service
        // This prevents 20-100GB/day storage growth from storing full HTML bodies

        String activeRateReservationId = null;
        boolean rateReservationHeld = false;
        boolean providerDispatchAccepted = false;

        try {
            ResolvedDeliveryContent deliveryContent = resolveDeliveryContent(
                    normalizedTenantId,
                    workspaceId,
                    campaignId,
                    messageId,
                    subject,
                    htmlBody,
                    contentReference);
            if (!deliveryContent.isAvailable()) {
                failSendForMissingContent(
                        logEntry,
                        normalizedTenantId,
                        workspaceId,
                        messageId,
                        campaignId,
                        jobId,
                        batchId,
                        subscriberId,
                        deliveryContent.failureReason());
                return;
            }
            subject = deliveryContent.subject();
            htmlBody = deliveryContent.htmlBody();
            contentReference = deliveryContent.contentReference();
            String policySnapshotFailure = validateSendGovernancePolicySnapshot(logEntry);
            if (policySnapshotFailure != null) {
                failSendForInvalidPolicySnapshot(
                        logEntry,
                        normalizedTenantId,
                        workspaceId,
                        messageId,
                        campaignId,
                        jobId,
                        batchId,
                        subscriberId,
                        policySnapshotFailure);
                return;
            }

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
            var strategyResult = providerStrategy.selectProvider(normalizedTenantId, workspaceId, senderDomain);
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

            feedbackOutboxService.enqueueEmailSent(normalizedTenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId,
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
            feedbackOutboxService.enqueueEmailFailed(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, e.getMessage(),
                    safetyMetadata(logEntry));
            
            if (e.isPermanent()) {
                // Synthesize a bounce event for audience/suppression
                String senderDomain = extractDomain(logEntry.getEmail());
                feedbackOutboxService.enqueueEmailBounced(tenantId, workspaceId, logEntry.getEmail(), e.getMessage(), senderDomain,
                        safetyMetadata(logEntry));
            }
        } else {
            logEntry.setStatus(MessageLog.DeliveryStatus.PENDING.name());
            Instant nextRetry = retryDecision.nextRetryAt();
            logEntry.setNextRetryAt(nextRetry);
            
            feedbackOutboxService.enqueueRetryScheduled(tenantId, workspaceId, messageId, attempts, nextRetry.toString(), safetyMetadata(logEntry));
        }
    }

    private ResolvedDeliveryContent resolveDeliveryContent(String tenantId,
                                                           String workspaceId,
                                                           String campaignId,
                                                           String messageId,
                                                           String subject,
                                                           String htmlBody,
                                                           String contentReference) {
        String normalizedReference;
        try {
            normalizedReference = normalizeContentReference(contentReference);
        } catch (IllegalArgumentException e) {
            return ResolvedDeliveryContent.missing(e.getMessage());
        }
        if (normalizedReference == null) {
            return ResolvedDeliveryContent.missing("Delivery contentReference is required");
        }

        String resolvedSubject = normalize(subject);
        String resolvedHtmlBody = normalize(htmlBody);
        if (resolvedSubject == null || resolvedHtmlBody == null) {
            Map<String, String> referencedContent = fetchContentForRetry(
                    tenantId,
                    workspaceId,
                    campaignId,
                    messageId,
                    normalizedReference);
            if (!referencedContent.isEmpty()) {
                if (resolvedSubject == null) {
                    resolvedSubject = normalize(referencedContent.get("subject"));
                }
                if (resolvedHtmlBody == null) {
                    resolvedHtmlBody = normalize(referencedContent.get("htmlBody"));
                }
            }
        }

        if (resolvedSubject == null || resolvedHtmlBody == null) {
            return ResolvedDeliveryContent.missing(
                    "Delivery content is missing required subject/htmlBody for contentReference " + normalizedReference);
        }
        return ResolvedDeliveryContent.available(resolvedSubject, resolvedHtmlBody, normalizedReference);
    }

    private void failSendForMissingContent(MessageLog logEntry,
                                           String tenantId,
                                           String workspaceId,
                                           String messageId,
                                           String campaignId,
                                           String jobId,
                                           String batchId,
                                           String subscriberId,
                                           String reason) {
        String failureReason = markContentUnavailable(logEntry, reason);
        log.warn("Failing delivery for message {}: {}", messageId, failureReason);
        feedbackOutboxService.enqueueEmailFailed(
                tenantId,
                workspaceId,
                messageId,
                campaignId,
                jobId,
                batchId,
                subscriberId,
                failureReason,
                safetyMetadata(logEntry));
    }

    private void failSendForInvalidPolicySnapshot(MessageLog logEntry,
                                                  String tenantId,
                                                  String workspaceId,
                                                  String messageId,
                                                  String campaignId,
                                                  String jobId,
                                                  String batchId,
                                                  String subscriberId,
                                                  String reason) {
        String failureReason = markPolicySnapshotUnavailable(logEntry, reason);
        log.warn("Failing delivery for message {}: {}", messageId, failureReason);
        feedbackOutboxService.enqueueEmailFailed(
                tenantId,
                workspaceId,
                messageId,
                campaignId,
                jobId,
                batchId,
                subscriberId,
                failureReason,
                safetyMetadata(logEntry));
    }

    private String validateSendGovernancePolicySnapshot(MessageLog logEntry) {
        String policyId = normalize(logEntry.getSendGovernancePolicyId());
        String policyKey = normalize(logEntry.getSendGovernancePolicyKey());
        Long policyVersion = logEntry.getSendGovernancePolicyVersion();
        String snapshotHash = normalize(logEntry.getSendGovernancePolicySnapshotHash());
        String snapshotJson = normalize(logEntry.getSendGovernancePolicySnapshot());
        if (policyId == null || policyKey == null || policyVersion == null || snapshotHash == null || snapshotJson == null) {
            return "Send governance policy snapshot is required before delivery execution";
        }
        if (policyVersion < 0) {
            return "Send governance policy version is invalid";
        }
        if (!SNAPSHOT_HASH_PATTERN.matcher(snapshotHash).matches()) {
            return "Send governance policy snapshot hash is invalid";
        }
        SortedMap<String, Object> snapshot;
        try {
            snapshot = readSnapshotMap(snapshotJson);
        } catch (RuntimeException e) {
            return "Send governance policy snapshot is malformed";
        }
        String canonicalJson = writeSnapshotJson(snapshot);
        if (!snapshotHash.equalsIgnoreCase(sha256Hex(canonicalJson))) {
            return "Send governance policy snapshot hash does not match payload";
        }
        if (!policyId.equals(normalize(snapshot.get("policyId")))) {
            return "Send governance policy snapshot policyId does not match delivery payload";
        }
        if (!policyKey.equals(normalize(snapshot.get("policyKey")))) {
            return "Send governance policy snapshot policyKey does not match delivery payload";
        }
        if (!policyVersion.equals(parseLong(snapshot.get("version")))) {
            return "Send governance policy snapshot version does not match delivery payload";
        }
        if (!Boolean.TRUE.equals(booleanValue(snapshot.get("active")))) {
            return "Send governance policy snapshot is inactive";
        }
        boolean commercial = Boolean.TRUE.equals(booleanValue(snapshot.get("commercial")))
                || "COMMERCIAL".equalsIgnoreCase(defaultString(snapshot.get("classification")));
        if (commercial) {
            if (!Boolean.TRUE.equals(booleanValue(snapshot.get("suppressionRequired")))) {
                return "Commercial send governance policy snapshot must require suppression checks";
            }
            if (!"REQUIRED".equalsIgnoreCase(defaultString(snapshot.get("unsubscribePolicy")))) {
                return "Commercial send governance policy snapshot must require unsubscribe handling";
            }
        }
        Integer retentionDays = parseInteger(snapshot.get("sendLogRetentionDays"));
        if (retentionDays == null || retentionDays < 1) {
            return "Send governance policy snapshot must define positive send-log retention days";
        }
        return null;
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
        feedbackOutboxService.enqueueEmailFailed(tenantId, workspaceId, messageId, campaignId, jobId, batchId, subscriberId, reason,
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
        feedbackOutboxService.enqueueRetryScheduled(tenantId, workspaceId, messageId,
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
        if (logEntry.getMessageId() != null) {
            metadata.put("messageId", logEntry.getMessageId());
        }
        if (logEntry.getCampaignId() != null) {
            metadata.put("campaignId", logEntry.getCampaignId());
        }
        if (logEntry.getJobId() != null) {
            metadata.put("jobId", logEntry.getJobId());
        }
        if (logEntry.getBatchId() != null) {
            metadata.put("batchId", logEntry.getBatchId());
        }
        if (logEntry.getSubscriberId() != null) {
            metadata.put("subscriberId", logEntry.getSubscriberId());
        }
        if (logEntry.getProviderId() != null) {
            metadata.put("providerId", logEntry.getProviderId());
        }
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
        if (logEntry.getSendGovernancePolicyId() != null) {
            metadata.put("sendGovernancePolicyId", logEntry.getSendGovernancePolicyId());
        }
        if (logEntry.getSendGovernancePolicyKey() != null) {
            metadata.put("sendGovernancePolicyKey", logEntry.getSendGovernancePolicyKey());
        }
        if (logEntry.getSendGovernancePolicyVersion() != null) {
            metadata.put("sendGovernancePolicyVersion", String.valueOf(logEntry.getSendGovernancePolicyVersion()));
        }
        if (logEntry.getSendGovernancePolicySnapshotHash() != null) {
            metadata.put("sendGovernancePolicySnapshotHash", logEntry.getSendGovernancePolicySnapshotHash());
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

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String defaultString(Object value) {
        String normalized = normalize(value);
        return normalized == null ? "" : normalized;
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
        java.util.List<MessageLog> retries = messageLogRepository.findEligibleForRetry(
                Instant.now(),
                PageRequest.of(0, SCHEDULED_RETRY_BATCH_SIZE));
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
                payload.put("sendGovernancePolicyId", logEntry.getSendGovernancePolicyId());
                payload.put("sendGovernancePolicyKey", logEntry.getSendGovernancePolicyKey());
                payload.put("sendGovernancePolicyVersion", logEntry.getSendGovernancePolicyVersion());
                payload.put("sendGovernancePolicySnapshotHash", logEntry.getSendGovernancePolicySnapshotHash());
                payload.put("sendGovernancePolicySnapshot", logEntry.getSendGovernancePolicySnapshot());
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
        String failureReason = markContentUnavailable(logEntry, reason);
        log.warn("Failing retry for message {}: {}", logEntry.getMessageId(), failureReason);
        messageLogRepository.save(logEntry);
        feedbackOutboxService.enqueueEmailFailed(
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

    private String markContentUnavailable(MessageLog logEntry, String reason) {
        String failureReason = normalize(reason);
        if (failureReason == null) {
            failureReason = "Delivery content is unavailable";
        }
        logEntry.setStatus(MessageLog.DeliveryStatus.FAILED.name());
        logEntry.setFailureClass(CONTENT_UNAVAILABLE_FAILURE_CLASS);
        logEntry.setProviderResponse(failureReason);
        logEntry.setNextRetryAt(null);
        return failureReason;
    }

    private String markPolicySnapshotUnavailable(MessageLog logEntry, String reason) {
        String failureReason = normalize(reason);
        if (failureReason == null) {
            failureReason = "Send governance policy snapshot is unavailable";
        }
        logEntry.setStatus(MessageLog.DeliveryStatus.FAILED.name());
        logEntry.setFailureClass(GOVERNANCE_POLICY_FAILURE_CLASS);
        logEntry.setProviderResponse(failureReason);
        logEntry.setNextRetryAt(null);
        return failureReason;
    }

    private String canonicalSnapshotJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence text) {
            String raw = text.toString().trim();
            return raw.isEmpty() ? null : raw;
        }
        if (value instanceof Map<?, ?> map) {
            return writeSnapshotJson(normalizeSnapshotMap(map));
        }
        try {
            Map<String, Object> converted = objectMapper.convertValue(value, MAP_TYPE);
            return writeSnapshotJson(new TreeMap<>(converted));
        } catch (IllegalArgumentException e) {
            String raw = String.valueOf(value).trim();
            return raw.isEmpty() ? null : raw;
        }
    }

    private SortedMap<String, Object> readSnapshotMap(String snapshotJson) {
        try {
            return normalizeSnapshotMap(objectMapper.readValue(snapshotJson, MAP_TYPE));
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to read send governance policy snapshot", e);
        }
    }

    private SortedMap<String, Object> normalizeSnapshotMap(Map<?, ?> raw) {
        TreeMap<String, Object> normalized = new TreeMap<>();
        if (raw != null) {
            raw.forEach((key, value) -> {
                if (key != null) {
                    normalized.put(String.valueOf(key), value);
                }
            });
        }
        return normalized;
    }

    private String writeSnapshotJson(SortedMap<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize send governance policy snapshot", e);
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash send governance policy snapshot", e);
        }
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
        }

        // Fetch only by scoped rendered-content reference. Campaign-only lookup is intentionally not supported.
        if (normalizedReference != null) {
            try {
                Map<String, String> contentFromService = contentServiceClient.fetchRenderedContent(
                        tenantId,
                        workspaceId,
                        normalizedReference);
                if (!contentFromService.isEmpty()) {
                    validateReferencedContent(contentFromService, tenantId, workspaceId, campaignId, messageId, normalizedReference);
                    log.info("Retrieved content from content-service for rendered reference {}", normalizedReference);
                    return contentFromService;
                }
            } catch (Exception e) {
                log.error("Failed to fetch content from content-service for rendered reference {}: {}",
                        normalizedReference,
                        e.getMessage());
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

    private record ResolvedDeliveryContent(String subject,
                                           String htmlBody,
                                           String contentReference,
                                           String failureReason) {
        private static ResolvedDeliveryContent available(String subject, String htmlBody, String contentReference) {
            return new ResolvedDeliveryContent(subject, htmlBody, contentReference, null);
        }

        private static ResolvedDeliveryContent missing(String failureReason) {
            return new ResolvedDeliveryContent(null, null, null, failureReason);
        }

        private boolean isAvailable() {
            return failureReason == null;
        }
    }

}
