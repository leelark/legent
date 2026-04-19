package com.legent.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Date and time utility for consistent timestamp handling.
 */
public final class DateTimeUtil {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private DateTimeUtil() {
        // Utility class
    }

    /**
     * Returns the current UTC instant.
     */
    public static Instant nowUtc() {
        return Instant.now();
    }

    /**
     * Returns the current UTC ZonedDateTime.
     */
    public static ZonedDateTime nowZonedUtc() {
        return ZonedDateTime.now(UTC);
    }

    /**
     * Formats an Instant to ISO-8601 string.
     */
    public static String toIsoString(Instant instant) {
        return ISO_FORMATTER.format(instant);
    }

    /**
     * Parses an ISO-8601 string to Instant.
     */
    public static Instant fromIsoString(String isoString) {
        return Instant.parse(isoString);
    }
}
