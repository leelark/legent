package com.legent.common.util;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * Centralized ID generation utility.
 * Uses ULID (Universally Unique Lexicographically Sortable Identifier)
 * for all primary keys.
 */
public final class IdGenerator {

    private IdGenerator() {
        // Utility class
    }

    /**
     * Generates a new ULID string (26 characters, Crockford Base32).
     */
    public static String newId() {
        return UlidCreator.getUlid().toString();
    }

    /**
     * Generates a new ULID suitable for use as an idempotency key.
     */
    public static String newIdempotencyKey() {
        return "IK-" + newId();
    }

    /**
     * Generates a new correlation ID for distributed tracing.
     */
    public static String newCorrelationId() {
        return "COR-" + newId();
    }
}
