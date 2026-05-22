package com.legent.delivery.service;

import com.legent.common.util.IdGenerator;
import com.legent.delivery.domain.DeliveryReplayQueue;
import com.legent.delivery.domain.MessageLog;
import com.legent.delivery.domain.ProviderHealthStatus;
import com.legent.delivery.repository.DeliveryReplayQueueRepository;
import com.legent.delivery.repository.MessageLogRepository;
import com.legent.delivery.repository.ProviderHealthStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOperationsService {

    private final MessageLogRepository messageLogRepository;
    private final DeliveryReplayQueueRepository replayQueueRepository;
    private final ProviderHealthStatusRepository providerHealthStatusRepository;
    private final DeliveryOrchestrationService orchestrationService;

    public Map<String, Object> queueStats(String tenantId, String workspaceId) {
        long pending = messageLogRepository.countByTenantIdAndWorkspaceIdAndStatusAndDeletedAtIsNull(
                tenantId, workspaceId, MessageLog.DeliveryStatus.PENDING.name());
        long processing = messageLogRepository.countByTenantIdAndWorkspaceIdAndStatusAndDeletedAtIsNull(
                tenantId, workspaceId, MessageLog.DeliveryStatus.PROCESSING.name());
        long sent = messageLogRepository.countByTenantIdAndWorkspaceIdAndStatusAndDeletedAtIsNull(
                tenantId, workspaceId, MessageLog.DeliveryStatus.SENT.name());
        long failed = messageLogRepository.countByTenantIdAndWorkspaceIdAndStatusAndDeletedAtIsNull(
                tenantId, workspaceId, MessageLog.DeliveryStatus.FAILED.name());
        long replayPending = replayQueueRepository.countByTenantIdAndWorkspaceIdAndStatus(
                tenantId, workspaceId, DeliveryReplayQueue.ReplayStatus.PENDING.name());
        long replayFailed = replayQueueRepository.countByTenantIdAndWorkspaceIdAndStatus(
                tenantId, workspaceId, DeliveryReplayQueue.ReplayStatus.FAILED.name());

        List<ProviderHealthStatus> providerHealth = providerHealthStatusRepository.findByTenantIdAndWorkspaceId(tenantId, workspaceId);
        long unhealthyProviders = providerHealth.stream()
                .filter(status -> status.getCurrentStatus() != ProviderHealthStatus.HealthStatus.HEALTHY || status.isCircuitBreakerOpen())
                .count();

        Map<String, Object> response = new HashMap<>();
        response.put("pending", pending);
        response.put("processing", processing);
        response.put("sent", sent);
        response.put("failed", failed);
        response.put("replayPending", replayPending);
        response.put("replayFailed", replayFailed);
        response.put("unhealthyProviders", unhealthyProviders);
        response.put("updatedAt", Instant.now());
        return response;
    }

    public List<MessageLog> recentMessages(String tenantId, String workspaceId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return messageLogRepository.findByTenantIdAndWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                tenantId,
                workspaceId,
                PageRequest.of(0, safeLimit)
        );
    }

    @Transactional
    public MessageLog retryMessage(String tenantId, String workspaceId, String messageId, String reason) {
        MessageLog messageLog = messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(tenantId, workspaceId, messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found for retry"));
        messageLog.setStatus(MessageLog.DeliveryStatus.PENDING.name());
        messageLog.setFailureClass(reason != null && !reason.isBlank() ? reason : "MANUAL_RETRY");
        messageLog.setNextRetryAt(Instant.now());
        return messageLogRepository.save(messageLog);
    }

    @Transactional
    public DeliveryReplayQueue enqueueReplay(String tenantId, String workspaceId, String messageId, String reason) {
        MessageLog source = messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(tenantId, workspaceId, messageId)
                .orElseThrow(() -> new IllegalArgumentException("Source message not found for replay"));

        DeliveryReplayQueue replay = new DeliveryReplayQueue();
        replay.setId(IdGenerator.newId());
        replay.setTenantId(tenantId);
        replay.setWorkspaceId(workspaceId);
        replay.setOriginalMessageId(source.getMessageId());
        replay.setCampaignId(source.getCampaignId());
        replay.setSubscriberId(source.getSubscriberId());
        replay.setEmail(source.getEmail());
        replay.setProviderId(source.getProviderId());
        replay.setReplayReason(reason == null || reason.isBlank() ? "MANUAL_REPLAY" : reason);
        replay.setFailureClass(source.getFailureClass());
        replay.setSourceJobId(source.getJobId());
        replay.setSourceBatchId(source.getBatchId());
        replay.setStatus(DeliveryReplayQueue.ReplayStatus.PENDING.name());
        replay.setPriority(5);
        replay.setScheduledAt(Instant.now());
        replay.setOwnershipScope("WORKSPACE");
        return replayQueueRepository.save(replay);
    }

    @Transactional
    public int processReplayQueue(String tenantId, String workspaceId, int maxItems) {
        int safeMax = Math.max(1, Math.min(maxItems, 500));
        Instant now = Instant.now();
        List<DeliveryReplayQueue> queued = replayQueueRepository
                .findByTenantIdAndWorkspaceIdAndStatusAndScheduledAtLessThanEqualOrderByPriorityAscScheduledAtAsc(
                        tenantId,
                        workspaceId,
                        DeliveryReplayQueue.ReplayStatus.PENDING.name(),
                        now,
                        PageRequest.of(0, safeMax)
                );
        int processed = 0;
        for (DeliveryReplayQueue replay : queued) {
            int claimed = replayQueueRepository.claimForProcessing(
                    tenantId,
                    workspaceId,
                    replay.getId(),
                    DeliveryReplayQueue.ReplayStatus.PENDING.name(),
                    DeliveryReplayQueue.ReplayStatus.PROCESSING.name());
            if (claimed == 0) {
                log.debug("Skipping replay {} because another worker claimed or changed it", replay.getId());
                continue;
            }
            try {
                Optional<MessageLog> source = messageLogRepository.findByTenantIdAndWorkspaceIdAndMessageId(
                        replay.getTenantId(),
                        replay.getWorkspaceId(),
                        replay.getOriginalMessageId()
                );
                if (source.isEmpty()) {
                    throw new IllegalStateException("Missing source message for replay");
                }
                MessageLog sourceLog = source.get();
                Map<String, Object> payload = new HashMap<>();
                payload.put("workspaceId", replay.getWorkspaceId());
                payload.put("teamId", replay.getTeamId());
                payload.put("email", replay.getEmail());
                payload.put("subscriberId", replay.getSubscriberId());
                payload.put("campaignId", replay.getCampaignId());
                payload.put("jobId", replay.getSourceJobId());
                payload.put("batchId", replay.getSourceBatchId());
                payload.put("messageId", replay.getOriginalMessageId() + ":replay:" + IdGenerator.newId());
                payload.put("fromEmail", sourceLog.getFromEmail());
                payload.put("fromName", sourceLog.getFromName());
                payload.put("replyToEmail", sourceLog.getReplyToEmail());
                String contentReference = sourceLog.getContentReference();
                if (contentReference == null || contentReference.isBlank()) {
                    throw new IllegalStateException("Source message contentReference is required for replay");
                }
                payload.put("contentReference", contentReference);

                orchestrationService.processSendRequest(payload, replay.getTenantId(), payload.get("messageId").toString());
                int completed = replayQueueRepository.markCompleted(
                        replay.getTenantId(),
                        replay.getWorkspaceId(),
                        replay.getId(),
                        DeliveryReplayQueue.ReplayStatus.PROCESSING.name(),
                        DeliveryReplayQueue.ReplayStatus.COMPLETED.name(),
                        Instant.now());
                if (completed == 0) {
                    log.warn("Replay {} completed side effects but terminal status update was skipped", replay.getId());
                }
            } catch (Exception ex) {
                replayQueueRepository.markFailed(
                        replay.getTenantId(),
                        replay.getWorkspaceId(),
                        replay.getId(),
                        DeliveryReplayQueue.ReplayStatus.PROCESSING.name(),
                        DeliveryReplayQueue.ReplayStatus.FAILED.name(),
                        ex.getMessage());
                log.warn("Replay failed for {}: {}", replay.getId(), ex.getMessage());
            }
            processed++;
        }
        return processed;
    }

    public Map<String, Object> failureDiagnostics(String tenantId, String workspaceId) {
        List<MessageLog> recent = recentMessages(tenantId, workspaceId, 500);
        Map<String, Long> byFailureClass = new HashMap<>();
        long failed = 0;
        for (MessageLog logEntry : recent) {
            if (!MessageLog.DeliveryStatus.FAILED.name().equals(logEntry.getStatus())) {
                continue;
            }
            failed++;
            String failureClass = logEntry.getFailureClass() == null ? "UNKNOWN" : logEntry.getFailureClass();
            byFailureClass.put(failureClass, byFailureClass.getOrDefault(failureClass, 0L) + 1);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("failedRecent", failed);
        response.put("byFailureClass", byFailureClass);
        response.put("replayQueueDepth",
                replayQueueRepository.countByTenantIdAndWorkspaceIdAndStatus(tenantId, workspaceId, DeliveryReplayQueue.ReplayStatus.PENDING.name()));
        return response;
    }
}
