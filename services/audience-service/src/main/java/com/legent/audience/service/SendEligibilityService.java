package com.legent.audience.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    @Transactional(readOnly = true)
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
            subscribers.forEach(subscriber -> results.add(evaluate(tenantId, workspaceId, subscriber)));
        }
        return results;
    }

    public boolean isSendEligible(Subscriber subscriber) {
        return subscriber != null
                && statusAllowsSend(subscriber)
                && preferencesAllowEmail(subscriber);
    }

    public EligibilityResult evaluate(String tenantId, String workspaceId, Subscriber subscriber) {
        if (!statusAllowsSend(subscriber)) {
            return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), false, "STATUS_" + subscriber.getStatus());
        }
        if (!preferencesAllowEmail(subscriber)) {
            return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), false, "EMAIL_PREFERENCE_DISABLED");
        }
        boolean suppressed = !suppressionRepository.findActiveSuppression(tenantId, workspaceId, subscriber.getEmail()).isEmpty();
        if (suppressed) {
            return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), false, "SUPPRESSED");
        }
        return new EligibilityResult(subscriber.getId(), subscriber.getEmail(), true, null);
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
        for (String key : List.of("email", "emailOptIn", "marketing", "campaigns")) {
            Object value = preferences.get(key);
            if (value instanceof Boolean bool && !bool) {
                return false;
            }
            if (value instanceof String text && "false".equals(text.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    public record EligibilityResult(String subscriberId, String email, boolean eligible, String reason) {
    }
}
