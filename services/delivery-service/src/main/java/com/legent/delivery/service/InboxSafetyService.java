package com.legent.delivery.service;

import com.legent.delivery.domain.InboxSafetyEvaluation;
import com.legent.delivery.repository.InboxSafetyEvaluationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InboxSafetyService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final InboxSafetyEvaluationRepository inboxSafetyEvaluationRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public InboxSafetyResult evaluate(InboxSafetyRequest request) {
        List<String> reasons = new ArrayList<>();
        List<String> fixes = new ArrayList<>();
        int risk = 0;

        String email = normalize(request.email());
        String recipientDomain = extractDomain(email);
        String senderDomain = normalizeDomain(request.senderDomain());
        if (senderDomain == null) {
            senderDomain = extractDomain(request.fromEmail());
        }

        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            risk += 100;
            reasons.add("INVALID_RECIPIENT");
            fixes.add("Use a valid recipient email before delivery.");
        }
        if (senderDomain == null) {
            risk += 35;
            reasons.add("MISSING_SENDER_DOMAIN");
            fixes.add("Configure a verified sender domain.");
        }
        if (isSuppressed(request.tenantId(), request.workspaceId(), email)) {
            risk += 100;
            reasons.add("RECIPIENT_SUPPRESSED");
            fixes.add("Remove suppressed or complained contacts from the audience.");
        }

        ContentRisk contentRisk = scoreContent(request.subject(), request.htmlBody(), request.campaignId());
        risk += contentRisk.score();
        reasons.addAll(contentRisk.reasons());
        fixes.addAll(contentRisk.fixes());

        TrendRisk trendRisk = scoreTrends(request.tenantId(), request.workspaceId(), senderDomain, recipientDomain);
        risk += trendRisk.score();
        reasons.addAll(trendRisk.reasons());
        fixes.addAll(trendRisk.fixes());

        if (request.engagementScore() != null && request.engagementScore() < 15) {
            risk += 20;
            reasons.add("LOW_RECIPIENT_ENGAGEMENT");
            fixes.add("Send to engaged recipients first and re-engage cold contacts slowly.");
        }
        if (request.attemptCount() != null && request.attemptCount() > 2) {
            risk += 12;
            reasons.add("RETRY_PRESSURE");
            fixes.add("Wait for provider or domain recovery before more retries.");
        }

        risk = Math.min(100, risk);
        SafetyDecision decision = decide(risk, reasons);
        int maxRate = maxRateFor(risk);
        int allowedAudience = decision == SafetyDecision.BLOCK ? 0 : maxRate;

        InboxSafetyEvaluation entity = new InboxSafetyEvaluation();
        entity.setTenantId(request.tenantId());
        entity.setWorkspaceId(request.workspaceId());
        entity.setCampaignId(request.campaignId());
        entity.setJobId(request.jobId());
        entity.setBatchId(request.batchId());
        entity.setMessageId(request.messageId());
        entity.setSubscriberId(request.subscriberId());
        entity.setEmail(email);
        entity.setSenderDomain(senderDomain);
        entity.setRecipientDomain(recipientDomain);
        entity.setProviderId(request.providerId());
        entity.setDecision(decision.name());
        entity.setRiskScore(risk);
        entity.setMaxRatePerMinute(maxRate);
        entity.setAllowedAudienceCount(allowedAudience);
        entity.setReasonCodes(String.join(",", reasons));
        entity.setRemediationHints(String.join("|", fixes));
        entity.setRateLimitKey(request.rateLimitKey());
        entity.setWarmupStage(request.warmupStage());
        inboxSafetyEvaluationRepository.save(entity);

        return new InboxSafetyResult(decision, risk, maxRate, allowedAudience, reasons, fixes, entity.getId());
    }

    private SafetyDecision decide(int risk, List<String> reasons) {
        if (reasons.contains("INVALID_RECIPIENT") || reasons.contains("RECIPIENT_SUPPRESSED")
                || reasons.contains("CONTENT_PLACEHOLDER") || reasons.contains("AUTH_OR_COMPLIANCE_BLOCK")) {
            return SafetyDecision.BLOCK;
        }
        if (risk >= 85) {
            return SafetyDecision.BLOCK;
        }
        if (risk >= 65) {
            return SafetyDecision.DEFER;
        }
        if (risk >= 40) {
            return SafetyDecision.THROTTLE;
        }
        return SafetyDecision.ALLOW;
    }

    private int maxRateFor(int risk) {
        if (risk < 20) {
            return 1000;
        }
        if (risk < 40) {
            return 500;
        }
        if (risk < 65) {
            return 120;
        }
        return 30;
    }

    private ContentRisk scoreContent(String subject, String htmlBody, String campaignId) {
        List<String> reasons = new ArrayList<>();
        List<String> fixes = new ArrayList<>();
        int score = 0;
        String subjectText = subject == null ? "" : subject.trim();
        String body = htmlBody == null ? "" : htmlBody.trim();
        String lowerBody = body.toLowerCase(Locale.ROOT);
        String lowerSubject = subjectText.toLowerCase(Locale.ROOT);

        if (body.isBlank() || lowerBody.contains("email content</body>")) {
            reasons.add("CONTENT_PLACEHOLDER");
            fixes.add("Replace placeholder body with reviewed campaign content.");
            score += 100;
        }
        if (subjectText.isBlank()) {
            reasons.add("MISSING_SUBJECT");
            fixes.add("Add a clear subject line.");
            score += 25;
        }
        if (subjectText.replaceAll("[^A-Za-z]", "").length() > 5
                && subjectText.replaceAll("[^A-Za-z]", "").equals(subjectText.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT))) {
            reasons.add("SHOUTING_SUBJECT");
            fixes.add("Avoid all-caps subject lines.");
            score += 20;
        }
        if (lowerSubject.contains("free money") || lowerSubject.contains("winner") || lowerBody.contains("viagra")
                || lowerBody.contains("double your income") || lowerBody.contains("no catch")) {
            reasons.add("SPAM_PATTERN");
            fixes.add("Remove spam-like phrases and claims.");
            score += 30;
        }
        if (lowerBody.contains("bit.ly") || lowerBody.contains("tinyurl")) {
            reasons.add("LOW_TRUST_LINK");
            fixes.add("Use branded tracking links and avoid public shorteners.");
            score += 20;
        }
        int linkCount = lowerBody.split("(?i)<a\\s+href=").length - 1;
        if (linkCount > 25) {
            reasons.add("HIGH_LINK_DENSITY");
            fixes.add("Reduce link count and simplify the message.");
            score += 20;
        }
        if (campaignId != null && !campaignId.isBlank() && !lowerBody.contains("unsubscribe")) {
            reasons.add("MISSING_UNSUBSCRIBE");
            fixes.add("Add unsubscribe or preference-management footer.");
            score += 35;
        }
        if (score >= 85 && reasons.contains("MISSING_UNSUBSCRIBE")) {
            reasons.add("AUTH_OR_COMPLIANCE_BLOCK");
        }
        return new ContentRisk(score, reasons, fixes);
    }

    private TrendRisk scoreTrends(String tenantId, String workspaceId, String senderDomain, String recipientDomain) {
        List<String> reasons = new ArrayList<>();
        List<String> fixes = new ArrayList<>();
        int score = 0;
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long failures = count("""
                SELECT COUNT(*) FROM message_logs
                WHERE tenant_id = ? AND workspace_id = ? AND created_at >= ?
                  AND status IN ('FAILED','BOUNCED','COMPLAINED')
                """, tenantId, workspaceId, since);
        long sent = count("""
                SELECT COUNT(*) FROM message_logs
                WHERE tenant_id = ? AND workspace_id = ? AND created_at >= ?
                  AND status IN ('SENT','FAILED','BOUNCED','COMPLAINED')
                """, tenantId, workspaceId, since);
        if (sent >= 25) {
            double failureRate = failures / (double) sent;
            if (failureRate >= 0.10) {
                score += 35;
                reasons.add("HIGH_FAILURE_RATE");
                fixes.add("Pause risky sends, clean audience, and verify provider/domain health.");
            } else if (failureRate >= 0.04) {
                score += 15;
                reasons.add("ELEVATED_FAILURE_RATE");
                fixes.add("Throttle send rate until failure rate recovers.");
            }
        }
        long complaints = count("""
                SELECT COUNT(*) FROM message_logs
                WHERE tenant_id = ? AND workspace_id = ? AND created_at >= ?
                  AND failure_class = 'COMPLAINT'
                """, tenantId, workspaceId, since);
        if (complaints > 0) {
            score += 40;
            reasons.add("COMPLAINT_SIGNAL");
            fixes.add("Suppress complainers and lower send volume immediately.");
        }
        return new TrendRisk(score, reasons, fixes);
    }

    private boolean isSuppressed(String tenantId, String workspaceId, String email) {
        if (email == null) {
            return false;
        }
        Long count = countObject("""
                SELECT COUNT(*) FROM suppression_signals
                WHERE tenant_id = ? AND workspace_id = ? AND lower(email) = lower(?)
                  AND type IN ('HARD_BOUNCE', 'SOFT_BOUNCE', 'COMPLAINT')
                  AND deleted_at IS NULL
                """, tenantId, workspaceId, email);
        return count != null && count > 0;
    }

    private long count(String sql, Object... args) {
        Long result = countObject(sql, args);
        return result == null ? 0 : result;
    }

    private Long countObject(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, args);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeDomain(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return normalized.startsWith("@") ? normalized.substring(1) : normalized;
    }

    private String extractDomain(String email) {
        String normalized = normalize(email);
        if (normalized == null) {
            return null;
        }
        int idx = normalized.lastIndexOf("@");
        if (idx <= 0 || idx >= normalized.length() - 1) {
            return null;
        }
        return normalized.substring(idx + 1);
    }

    private record ContentRisk(int score, List<String> reasons, List<String> fixes) {}
    private record TrendRisk(int score, List<String> reasons, List<String> fixes) {}

    public enum SafetyDecision {
        ALLOW, THROTTLE, DEFER, BLOCK
    }

    public record InboxSafetyRequest(String tenantId,
                                     String workspaceId,
                                     String campaignId,
                                     String jobId,
                                     String batchId,
                                     String messageId,
                                     String subscriberId,
                                     String email,
                                     String fromEmail,
                                     String senderDomain,
                                     String providerId,
                                     String subject,
                                     String htmlBody,
                                     Integer engagementScore,
                                     Integer attemptCount,
                                     String rateLimitKey,
                                     String warmupStage) {}

    public record InboxSafetyResult(SafetyDecision decision,
                                    int riskScore,
                                    int maxRatePerMinute,
                                    int allowedAudienceCount,
                                    List<String> reasonCodes,
                                    List<String> remediationHints,
                                    String evaluationId) {}
}
