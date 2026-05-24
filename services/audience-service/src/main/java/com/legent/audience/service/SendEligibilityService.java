package com.legent.audience.service;

import java.util.ArrayList;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.audience.repository.SuppressionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SendEligibilityService {

    private final SubscriberRepository subscriberRepository;
    private final SuppressionRepository suppressionRepository;
    private final ContactLifecycleAuditService lifecycleAuditService;

    @Transactional
    public List<EligibilityResult> check(String tenantId, String workspaceId, List<String> emails, List<String> subscriberIds) {
        List<EligibilityResult> results = new ArrayList<>();
        if (emails != null) {
            for (String email : emails) {
                subscriberRepository.findByTenantIdAndWorkspaceIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, workspaceId, email)
                        .ifPresentOrElse(
                                subscriber -> results.add(evaluate(tenantId, workspaceId, subscriber)),
                                () -> results.add(new EligibilityResult(null, email, false, "SUBSCRIBER_NOT_FOUND")));
            }
        }
        if (subscriberIds != null && !subscriberIds.isEmpty()) {
            List<Subscriber> subscribers = subscriberRepository.findByTenantIdAndWorkspaceIdAndIdInAndDeletedAtIsNull(
                    tenantId, workspaceId, subscriberIds);
            results.addAll(evaluateAll(tenantId, workspaceId, subscribers));
        }
        lifecycleAuditService.sendEligibilityChecked(
                tenantId,
                workspaceId,
                results,
                emails == null ? 0 : emails.size(),
                subscriberIds == null ? 0 : subscriberIds.size());
        return results;
    }

    public EligibilityResult evaluate(String tenantId, String workspaceId, Subscriber subscriber) {
        EligibilityResult ineligibleResult = evaluateBeforeSuppression(subscriber);
        if (ineligibleResult != null) {
            return ineligibleResult;
        }
        boolean suppressed = !suppressionRepository.findActiveSuppression(tenantId, workspaceId, subscriber.getEmail()).isEmpty();
        if (suppressed) {
            return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), false, "SUPPRESSED");
        }
        return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), true, null);
    }

    public List<EligibilityResult> evaluateAll(String tenantId, String workspaceId, List<Subscriber> subscribers) {
        if (subscribers == null || subscribers.isEmpty()) {
            return List.of();
        }

        List<Subscriber> safeSubscribers = new ArrayList<>(subscribers);
        List<Subscriber> suppressionCandidates = safeSubscribers.stream()
                .filter(subscriber -> evaluateBeforeSuppression(subscriber) == null)
                .toList();
        Set<String> suppressedEmails = activeSuppressedEmails(tenantId, workspaceId, suppressionCandidates);

        return safeSubscribers.stream()
                .map(subscriber -> {
                    EligibilityResult ineligibleResult = evaluateBeforeSuppression(subscriber);
                    if (ineligibleResult != null) {
                        return ineligibleResult;
                    }
                    if (suppressedEmails.contains(normalizeEmail(subscriber.getEmail()))) {
                        return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), false, "SUPPRESSED");
                    }
                    return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), true, null);
                })
                .toList();
    }

    private EligibilityResult evaluateBeforeSuppression(Subscriber subscriber) {
        if (subscriber == null) {
            return new EligibilityResult(null, null, false, "SUBSCRIBER_NOT_FOUND");
        }
        if (!statusAllowsSend(subscriber)) {
            String status = subscriber.getStatus() == null ? "UNKNOWN" : subscriber.getStatus().name();
            return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), false, "STATUS_" + status);
        }
        if (!preferencesAllowEmail(subscriber)) {
            return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), false, "EMAIL_PREFERENCE_DISABLED");
        }
        return null;
    }

    private Set<String> activeSuppressedEmails(String tenantId, String workspaceId, List<Subscriber> subscribers) {
        List<String> emails = subscribers.stream()
                .map(Subscriber::getEmail)
                .map(SendEligibilityService::normalizeEmail)
                .filter(email -> email != null)
                .distinct()
                .toList();
        if (emails.isEmpty()) {
            return Set.of();
        }
        return suppressionRepository.findActiveSuppressedEmails(tenantId, workspaceId, emails).stream()
                .map(SendEligibilityService::normalizeEmail)
                .filter(email -> email != null)
                .collect(Collectors.toSet());
    }

    private boolean statusAllowsSend(Subscriber subscriber) {
        if (subscriber == null || subscriber.getStatus() == null) {
            return false;
        }
        return subscriber.getStatus() == Subscriber.SubscriberStatus.ACTIVE
                || subscriber.getStatus() == Subscriber.SubscriberStatus.SUBSCRIBED;
    }

    private boolean preferencesAllowEmail(Subscriber subscriber) {
        Map<String, Object> preferences = subscriber.getChannelPreferences();
        if (preferences == null || preferences.isEmpty()) {
            return true;
        }
        if (isFuturePaused(findMapValue(preferences, "pausedUntil"))) {
            return false;
        }
        for (String key : List.of("email", "emailOptIn", "marketing", "campaigns")) {
            if (isFalseValue(findMapValue(preferences, key))) {
                return false;
            }
        }
        Object channels = findMapValue(preferences, "channels");
        if (channels instanceof Map<?, ?> channelPreferences
                && isFalseValue(findMapValue(channelPreferences, "email"))) {
            return false;
        }
        return true;
    }

    private static boolean isFalseValue(Object value) {
        if (value instanceof Boolean bool) {
            return !bool;
        }
        return value instanceof String text
                && "false".equals(text.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isFuturePaused(Object value) {
        if (value == null) {
            return false;
        }
        try {
            return Instant.parse(String.valueOf(value)).isAfter(Instant.now());
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static Object findMapValue(Map<?, ?> map, String key) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && key.equalsIgnoreCase(String.valueOf(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    public record EligibilityResult(String subscriberId, String email, boolean eligible, String reason) {
    }
}
