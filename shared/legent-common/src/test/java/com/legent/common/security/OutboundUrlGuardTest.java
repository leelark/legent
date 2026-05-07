package com.legent.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboundUrlGuardTest {

    @Test
    void requirePublicHttpsUri_allowsPublicHttpsAddress() {
        assertDoesNotThrow(() -> OutboundUrlGuard.requirePublicHttpsUri("https://93.184.216.34/webhook", "webhook"));
    }

    @Test
    void requirePublicHttpsUri_rejectsLocalhostPrivateAndMetadataAddresses() {
        assertThrows(IllegalArgumentException.class,
                () -> OutboundUrlGuard.requirePublicHttpsUri("https://localhost/webhook", "webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> OutboundUrlGuard.requirePublicHttpsUri("https://10.0.0.5/webhook", "webhook"));
        assertThrows(IllegalArgumentException.class,
                () -> OutboundUrlGuard.requirePublicHttpsUri("https://169.254.169.254/latest/meta-data", "webhook"));
    }

    @Test
    void requirePublicHttpsUri_rejectsHttpByDefault() {
        assertThrows(IllegalArgumentException.class,
                () -> OutboundUrlGuard.requirePublicHttpsUri("http://93.184.216.34/webhook", "webhook"));
    }
}
