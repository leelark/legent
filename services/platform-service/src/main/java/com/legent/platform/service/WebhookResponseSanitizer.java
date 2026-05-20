package com.legent.platform.service;

import java.util.regex.Pattern;

final class WebhookResponseSanitizer {

    private static final int MAX_RESPONSE_BODY_CHARS = 1000;
    private static final String REDACTED = "[redacted]";
    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?i)(\"?(?:password|passwd|secret|token|api[_-]?key|authorization|cookie|set-cookie|private[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret)\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^\\s,}\\]]+)");
    private static final Pattern AUTH_SCHEME_VALUE = Pattern.compile(
            "(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+");

    private WebhookResponseSanitizer() {
    }

    static String sanitize(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = SENSITIVE_KEY_VALUE.matcher(value).replaceAll("$1\"" + REDACTED + "\"");
        sanitized = AUTH_SCHEME_VALUE.matcher(sanitized).replaceAll("$1 " + REDACTED);
        if (sanitized.length() > MAX_RESPONSE_BODY_CHARS) {
            return sanitized.substring(0, MAX_RESPONSE_BODY_CHARS);
        }
        return sanitized;
    }
}
