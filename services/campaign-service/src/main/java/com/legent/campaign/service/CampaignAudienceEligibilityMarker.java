package com.legent.campaign.service;

import com.legent.common.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;

final class CampaignAudienceEligibilityMarker {

    static final String FIELD_NAME = "audienceEligibilityMarker";
    static final String PASSED_VALUE = "audience-resolution-final-eligibility:v1:passed";
    static final String FAILURE_CODE = "AUDIENCE_ELIGIBILITY_MARKER_REQUIRED";

    private CampaignAudienceEligibilityMarker() {
    }

    static Map<String, String> markEligible(Map<String, String> subscriber) {
        Map<String, String> marked = new LinkedHashMap<>();
        if (subscriber != null) {
            marked.putAll(subscriber);
        }
        marked.put(FIELD_NAME, PASSED_VALUE);
        return marked;
    }

    static Map<String, String> requireEligible(Map<String, String> subscriber, String source) {
        if (!hasPassedMarker(subscriber)) {
            throw new MissingAudienceEligibilityMarkerException(source);
        }
        return subscriber;
    }

    static Map<String, String> withoutMarker(Map<String, String> subscriber) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        if (subscriber != null) {
            cleaned.putAll(subscriber);
            cleaned.remove(FIELD_NAME);
        }
        return cleaned;
    }

    private static boolean hasPassedMarker(Map<String, ?> subscriber) {
        return subscriber != null && PASSED_VALUE.equals(String.valueOf(subscriber.get(FIELD_NAME)));
    }

    static final class MissingAudienceEligibilityMarkerException extends ValidationException {
        MissingAudienceEligibilityMarkerException(String source) {
            super("audienceEligibility", source + " is missing final audience eligibility marker");
        }
    }
}
