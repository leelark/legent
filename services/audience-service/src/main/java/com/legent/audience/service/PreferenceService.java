package com.legent.audience.service;

import com.legent.audience.domain.Subscriber;
import com.legent.audience.domain.Suppression;
import com.legent.audience.dto.PreferenceDto;
import com.legent.audience.repository.SubscriberRepository;
import com.legent.audience.repository.SuppressionRepository;
import com.legent.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final SubscriberRepository subscriberRepository;
    private final SuppressionRepository suppressionRepository;

    @Transactional(readOnly = true)
    public PreferenceDto.Response get(String subscriberId) {
        Subscriber subscriber = getScopedSubscriber(subscriberId);
        Map<String, Object> prefs = subscriber.getChannelPreferences() == null
                ? new HashMap<>()
                : new HashMap<>(subscriber.getChannelPreferences());
        return PreferenceDto.Response.builder()
                .subscriberId(subscriberId)
                .status(subscriber.getStatus().name())
                .topicSubscriptions(mapValue(prefs.get("topicSubscriptions")))
                .channelPreferences(mapValue(prefs.get("channels")))
                .communicationFrequency(strValue(prefs.get("frequency")))
                .preferredLanguage(strValue(prefs.get("preferredLanguage")))
                .preferredBrand(strValue(prefs.get("preferredBrand")))
                .pausedUntil(instantValue(prefs.get("pausedUntil")))
                .build();
    }

    @Transactional
    public PreferenceDto.Response update(String subscriberId, PreferenceDto.UpdateRequest request) {
        Subscriber subscriber = getScopedSubscriber(subscriberId);
        Map<String, Object> prefs = subscriber.getChannelPreferences() == null
                ? new HashMap<>()
                : new HashMap<>(subscriber.getChannelPreferences());
        if (request.getTopicSubscriptions() != null) prefs.put("topicSubscriptions", request.getTopicSubscriptions());
        if (request.getChannelPreferences() != null) prefs.put("channels", request.getChannelPreferences());
        if (request.getCommunicationFrequency() != null) prefs.put("frequency", request.getCommunicationFrequency());
        if (request.getPreferredLanguage() != null) prefs.put("preferredLanguage", request.getPreferredLanguage());
        if (request.getPreferredBrand() != null) prefs.put("preferredBrand", request.getPreferredBrand());
        subscriber.setChannelPreferences(prefs);
        subscriberRepository.save(subscriber);
        return get(subscriberId);
    }

    @Transactional
    public PreferenceDto.Response pause(String subscriberId, PreferenceDto.PauseRequest request) {
        Subscriber subscriber = getScopedSubscriber(subscriberId);
        Map<String, Object> prefs = subscriber.getChannelPreferences() == null
                ? new HashMap<>()
                : new HashMap<>(subscriber.getChannelPreferences());
        prefs.put("pausedUntil", request.getPausedUntil() == null ? Instant.now().plusSeconds(7 * 24 * 3600) : request.getPausedUntil().toString());
        prefs.put("pauseReason", request.getReason());
        subscriber.setChannelPreferences(prefs);
        subscriberRepository.save(subscriber);
        return get(subscriberId);
    }

    @Transactional
    public PreferenceDto.Response unsubscribe(String subscriberId, PreferenceDto.UnsubscribeRequest request) {
        Subscriber subscriber = getScopedSubscriber(subscriberId);
        subscriber.setStatus(Subscriber.SubscriberStatus.UNSUBSCRIBED);
        subscriber.setUnsubscribedAt(Instant.now());
        subscriberRepository.save(subscriber);

        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        String normalizedEmail = subscriber.getEmail().toLowerCase().trim();

        if (!suppressionRepository.existsByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                tenantId, workspaceId, normalizedEmail, Suppression.SuppressionType.UNSUBSCRIBE)) {
            Suppression suppression = new Suppression();
            suppression.setTenantId(tenantId);
            suppression.setWorkspaceId(workspaceId);
            suppression.setEmail(normalizedEmail);
            suppression.setSuppressionType(Suppression.SuppressionType.UNSUBSCRIBE);
            suppression.setReason(request.getReason());
            suppression.setSource("PREFERENCE_CENTER");
            suppressionRepository.save(suppression);
        }
        return get(subscriberId);
    }

    @Transactional
    public PreferenceDto.Response resubscribe(String subscriberId) {
        Subscriber subscriber = getScopedSubscriber(subscriberId);
        subscriber.setStatus(Subscriber.SubscriberStatus.ACTIVE);
        subscriber.setUnsubscribedAt(null);
        subscriberRepository.save(subscriber);

        String tenantId = AudienceScope.tenantId();
        String workspaceId = AudienceScope.workspaceId();
        String normalizedEmail = subscriber.getEmail().toLowerCase().trim();
        suppressionRepository.findByTenantIdAndWorkspaceIdAndEmailAndSuppressionTypeAndDeletedAtIsNull(
                        tenantId, workspaceId, normalizedEmail, Suppression.SuppressionType.UNSUBSCRIBE)
                .ifPresent(suppression -> {
                    suppression.setRecoveryStatus("RECOVERED");
                    suppression.setRecoveredAt(Instant.now());
                    suppression.softDelete();
                    suppressionRepository.save(suppression);
                });

        return get(subscriberId);
    }

    private Subscriber getScopedSubscriber(String subscriberId) {
        return subscriberRepository.findByTenantIdAndWorkspaceIdAndId(
                        AudienceScope.tenantId(),
                        AudienceScope.workspaceId(),
                        subscriberId)
                .orElseThrow(() -> new NotFoundException("Subscriber", subscriberId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private String strValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Instant instantValue(Object value) {
        if (value == null) return null;
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
