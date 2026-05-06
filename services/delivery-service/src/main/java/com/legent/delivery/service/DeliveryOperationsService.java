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

        List<ProviderHealthStatus> providerHealth = providerHealthStatusRepository.findByTenantId(tenantId);
        long unhealthyProviders = providerHealth.stream()
                .filter(status -> workspaceId.equals(status.getWorkspaceId()))
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
        List<DeliveryReplayQueue> queued = replayQueueRepository
                .findByTenantIdAndWorkspaceIdAndStatusAndScheduledAtLessThanEqualOrderByPriorityAscScheduledAtAsc(
                        tenantId,
                        workspaceId,
                        DeliveryReplayQueue.ReplayStatus.PENDING.name(),
                        Instant.now()
                );
        int processed = 0;
        for (DeliveryReplayQueue replay : queued) {
            if (processed >= safeMax) {
                break;
            }
            replay.setStatus(DeliveryReplayQueue.ReplayStatus.PROCESSING.name());
            replayQueueRepository.save(replay);
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

                orchestrationService.processSendRequest(payload, replay.getTenantId(), payload.get("messageId").toString());
                replay.setStatus(DeliveryReplayQueue.ReplayStatus.COMPLETED.name());
                replay.setProcessedAt(Instant.now());
                replay.setErrorMessage(null);
            } catch (Exception ex) {
                replay.setStatus(DeliveryReplayQueue.ReplayStatus.FAILED.name());
                replay.setRetryCount((replay.getRetryCount() == null ? 0 : replay.getRetryCount()) + 1);
                replay.setErrorMessage(ex.getMessage());
                log.warn("Replay failed for {}: {}", replay.getId(), ex.getMessage());
            }
            replayQueueRepository.save(replay);
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

