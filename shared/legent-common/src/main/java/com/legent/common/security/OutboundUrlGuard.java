package com.legent.common.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Validates tenant/provider supplied outbound URLs before any network call.
 */
public final class OutboundUrlGuard {
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "localhost.localdomain",
            "metadata.google.internal"
    );

    private OutboundUrlGuard() {
    }

    public static URI requirePublicHttpsUri(String rawUrl, String label) {
        return validate(rawUrl, label, true);
    }

    public static URI requirePublicUri(String rawUrl, String label, boolean requireHttps) {
        return validate(rawUrl, label, requireHttps);
    }

    private static URI validate(String rawUrl, String label, boolean requireHttps) {
        String displayLabel = label == null || label.isBlank() ? "outbound URL" : label;
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException(displayLabel + " is required");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(displayLabel + " is not a valid URI", ex);
        }

        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme == null || (!"https".equals(scheme) && !"http".equals(scheme))) {
            throw new IllegalArgumentException(displayLabel + " must use http or https");
        }
        if (requireHttps && !"https".equals(scheme)) {
            throw new IllegalArgumentException(displayLabel + " must use https");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(displayLabel + " must not include user info");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(displayLabel + " must include a host");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (BLOCKED_HOSTS.contains(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            throw new IllegalArgumentException(displayLabel + " host is not allowed");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception ex) {
            throw new IllegalArgumentException(displayLabel + " host could not be resolved", ex);
        }
        if (addresses.length == 0) {
            throw new IllegalArgumentException(displayLabel + " host could not be resolved");
        }
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException(displayLabel + " resolves to a private or reserved address");
            }
        }

        return uri;
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            return isBlockedIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isBlockedIpv6(address.getAddress());
        }
        return true;
    }

    private static boolean isBlockedIpv4(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19));
    }

    private static boolean isBlockedIpv6(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return (first & 0xfe) == 0xfc
                || (first == 0xfe && (second & 0xc0) == 0x80);
    }
}
