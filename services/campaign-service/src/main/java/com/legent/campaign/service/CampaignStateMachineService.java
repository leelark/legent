package com.legent.campaign.service;

import com.legent.campaign.domain.Campaign;
import com.legent.campaign.domain.SendJob;
import com.legent.common.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Centralized finite-state transitions for campaign and send-job lifecycles.
 */
@Service
public class CampaignStateMachineService {

    private static final Map<Campaign.CampaignStatus, Set<Campaign.CampaignStatus>> CAMPAIGN_TRANSITIONS =
            new EnumMap<>(Campaign.CampaignStatus.class);
    private static final Map<SendJob.JobStatus, Set<SendJob.JobStatus>> JOB_TRANSITIONS =
            new EnumMap<>(SendJob.JobStatus.class);

    static {
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.DRAFT,
                EnumSet.of(Campaign.CampaignStatus.REVIEW_PENDING, Campaign.CampaignStatus.APPROVED, Campaign.CampaignStatus.SCHEDULED,
                        Campaign.CampaignStatus.SENDING, Campaign.CampaignStatus.ARCHIVED));
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.REVIEW_PENDING,
                EnumSet.of(Campaign.CampaignStatus.APPROVED, Campaign.CampaignStatus.DRAFT, Campaign.CampaignStatus.ARCHIVED));
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.APPROVED,
                EnumSet.of(Campaign.CampaignStatus.SCHEDULED, Campaign.CampaignStatus.SENDING, Campaign.CampaignStatus.DRAFT,
                        Campaign.CampaignStatus.ARCHIVED));
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.SCHEDULED,
                EnumSet.of(Campaign.CampaignStatus.SENDING, Campaign.CampaignStatus.PAUSED, Campaign.CampaignStatus.CANCELLED,
                        Campaign.CampaignStatus.FAILED, Campaign.CampaignStatus.ARCHIVED));
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.SENDING,
                EnumSet.of(Campaign.CampaignStatus.PAUSED, Campaign.CampaignStatus.COMPLETED, Campaign.CampaignStatus.FAILED,
                        Campaign.CampaignStatus.CANCELLED));
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.PAUSED,
                EnumSet.of(Campaign.CampaignStatus.SENDING, Campaign.CampaignStatus.CANCELLED, Campaign.CampaignStatus.FAILED));
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.COMPLETED,
                EnumSet.of(Campaign.CampaignStatus.ARCHIVED, Campaign.CampaignStatus.APPROVED));
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.FAILED,
                EnumSet.of(Campaign.CampaignStatus.DRAFT, Campaign.CampaignStatus.SCHEDULED, Campaign.CampaignStatus.SENDING, Campaign.CampaignStatus.ARCHIVED));
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.CANCELLED,
                EnumSet.of(Campaign.CampaignStatus.DRAFT, Campaign.CampaignStatus.ARCHIVED));
        CAMPAIGN_TRANSITIONS.put(Campaign.CampaignStatus.ARCHIVED, EnumSet.of(Campaign.CampaignStatus.DRAFT));

        JOB_TRANSITIONS.put(SendJob.JobStatus.PENDING, EnumSet.of(SendJob.JobStatus.RESOLVING, SendJob.JobStatus.CANCELLED));
        JOB_TRANSITIONS.put(SendJob.JobStatus.RESOLVING,
                EnumSet.of(SendJob.JobStatus.BATCHING, SendJob.JobStatus.SENDING, SendJob.JobStatus.COMPLETED, SendJob.JobStatus.PAUSED, SendJob.JobStatus.FAILED, SendJob.JobStatus.CANCELLED));
        JOB_TRANSITIONS.put(SendJob.JobStatus.BATCHING,
                EnumSet.of(SendJob.JobStatus.SENDING, SendJob.JobStatus.PAUSED, SendJob.JobStatus.FAILED, SendJob.JobStatus.CANCELLED));
        JOB_TRANSITIONS.put(SendJob.JobStatus.SENDING,
                EnumSet.of(SendJob.JobStatus.PAUSED, SendJob.JobStatus.COMPLETED, SendJob.JobStatus.FAILED, SendJob.JobStatus.CANCELLED));
        JOB_TRANSITIONS.put(SendJob.JobStatus.PAUSED,
                EnumSet.of(SendJob.JobStatus.SENDING, SendJob.JobStatus.CANCELLED, SendJob.JobStatus.FAILED));
        JOB_TRANSITIONS.put(SendJob.JobStatus.RETRYING,
                EnumSet.of(SendJob.JobStatus.SENDING, SendJob.JobStatus.FAILED, SendJob.JobStatus.CANCELLED));
        JOB_TRANSITIONS.put(SendJob.JobStatus.COMPLETED, EnumSet.noneOf(SendJob.JobStatus.class));
        JOB_TRANSITIONS.put(SendJob.JobStatus.FAILED, EnumSet.of(SendJob.JobStatus.RETRYING, SendJob.JobStatus.CANCELLED));
        JOB_TRANSITIONS.put(SendJob.JobStatus.CANCELLED, EnumSet.noneOf(SendJob.JobStatus.class));
    }

    public void transitionCampaign(Campaign campaign, Campaign.CampaignStatus nextStatus, String note) {
        assertCampaignTransition(campaign.getStatus(), nextStatus);
        campaign.setStatus(nextStatus);
        if (note != null && !note.isBlank()) {
            campaign.setLifecycleNote(note);
        }
        if (nextStatus == Campaign.CampaignStatus.ARCHIVED) {
            campaign.setArchivedAt(Instant.now());
        } else if (campaign.getArchivedAt() != null && nextStatus != Campaign.CampaignStatus.ARCHIVED) {
            campaign.setArchivedAt(null);
        }
    }

    public void transitionJob(SendJob job, SendJob.JobStatus nextStatus, String note) {
        assertJobTransition(job.getStatus(), nextStatus);
        job.setStatus(nextStatus);
        if (note != null && !note.isBlank()) {
            job.setErrorMessage(note);
        }
        if (nextStatus == SendJob.JobStatus.PAUSED) {
            job.setPausedAt(Instant.now());
        }
        if (nextStatus == SendJob.JobStatus.CANCELLED) {
            job.setCancelledAt(Instant.now());
        }
        if (nextStatus == SendJob.JobStatus.COMPLETED) {
            job.setCompletedAt(Instant.now());
        }
    }

    public void assertCampaignTransition(Campaign.CampaignStatus from, Campaign.CampaignStatus to) {
        if (from == to) {
            return;
        }
        Set<Campaign.CampaignStatus> allowed = CAMPAIGN_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(Campaign.CampaignStatus.class));
        if (!allowed.contains(to)) {
            throw new ValidationException("campaign.status", "Invalid campaign transition: " + from + " -> " + to);
        }
    }

    public void assertJobTransition(SendJob.JobStatus from, SendJob.JobStatus to) {
        if (from == to) {
            return;
        }
        Set<SendJob.JobStatus> allowed = JOB_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(SendJob.JobStatus.class));
        if (!allowed.contains(to)) {
            throw new ValidationException("sendJob.status", "Invalid send job transition: " + from + " -> " + to);
        }
    }
}
