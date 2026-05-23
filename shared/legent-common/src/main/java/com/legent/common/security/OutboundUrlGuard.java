package com.legent.common.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
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

    public static URI requirePublicUriSyntax(String rawUrl, String label, boolean requireHttps) {
        URI uri = validateSyntax(rawUrl, label, requireHttps);
        rejectBlockedLiteralAddress(uri.getHost(), label);
        return uri;
    }

    public static InetAddress requirePublicResolvedAddress(InetAddress address, String label) {
        String displayLabel = label == null || label.isBlank() ? "outbound URL" : label;
        if (address == null) {
            throw new IllegalArgumentException(displayLabel + " resolved address is required");
        }
        if (isBlockedAddress(address)) {
            throw new IllegalArgumentException(displayLabel + " resolves to a private or reserved address");
        }
        return address;
    }

    public static boolean isPublicAddress(InetAddress address) {
        return address != null && !isBlockedAddress(address);
    }

    private static URI validate(String rawUrl, String label, boolean requireHttps) {
        URI uri = validateSyntax(rawUrl, label, requireHttps);
        String host = uri.getHost();
        String displayLabel = displayLabel(label);

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
            requirePublicResolvedAddress(address, displayLabel);
        }

        return uri;
    }

    private static URI validateSyntax(String rawUrl, String label, boolean requireHttps) {
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

        return uri;
    }

    private static String displayLabel(String label) {
        return label == null || label.isBlank() ? "outbound URL" : label;
    }

    private static void rejectBlockedLiteralAddress(String host, String label) {
        if (host == null || host.isBlank()) {
            return;
        }
        byte[] literalAddress = parseIpLiteral(host);
        if (literalAddress == null) {
            return;
        }
        try {
            requirePublicResolvedAddress(InetAddress.getByAddress(literalAddress), displayLabel(label));
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }
            throw new IllegalArgumentException(displayLabel(label) + " host is not a valid IP address", ex);
        }
    }

    private static byte[] parseIpLiteral(String host) {
        String candidate = host;
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        if (candidate.indexOf(':') >= 0) {
            if (candidate.indexOf('.') >= 0) {
                return ipv4MappedIpv6Bytes(candidate);
            }
            return parseIpv6Literal(candidate);
        }
        if (candidate.indexOf('.') >= 0) {
            return parseIpv4Literal(candidate);
        }
        return null;
    }

    private static byte[] ipv4MappedIpv6Bytes(String host) {
        int lastColon = host.lastIndexOf(':');
        if (lastColon < 0 || lastColon == host.length() - 1) {
            return null;
        }
        byte[] ipv4 = parseIpv4Literal(host.substring(lastColon + 1));
        if (ipv4 == null) {
            return null;
        }
        String prefix = host.substring(0, lastColon);
        if (!prefix.equalsIgnoreCase("::ffff") && !prefix.equalsIgnoreCase("0:0:0:0:0:ffff")) {
            return null;
        }
        byte[] bytes = new byte[16];
        bytes[10] = (byte) 0xff;
        bytes[11] = (byte) 0xff;
        System.arraycopy(ipv4, 0, bytes, 12, 4);
        return bytes;
    }

    private static byte[] parseIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isBlank() || !part.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (value < 0 || value > 255) {
                return null;
            }
            bytes[i] = (byte) value;
        }
        return bytes;
    }

    private static byte[] parseIpv6Literal(String host) {
        String candidate = host;
        int zoneIndex = candidate.indexOf('%');
        if (zoneIndex >= 0) {
            candidate = candidate.substring(0, zoneIndex);
        }
        int compressionIndex = candidate.indexOf("::");
        if (compressionIndex != candidate.lastIndexOf("::")) {
            return null;
        }

        String[] headParts = compressionIndex >= 0
                ? splitIpv6Section(candidate.substring(0, compressionIndex))
                : splitIpv6Section(candidate);
        String[] tailParts = compressionIndex >= 0
                ? splitIpv6Section(candidate.substring(compressionIndex + 2))
                : new String[0];

        byte[] head = parseIpv6Groups(headParts);
        byte[] tail = parseIpv6Groups(tailParts);
        if (head == null || tail == null) {
            return null;
        }
        int totalGroups = (head.length + tail.length) / 2;
        if (compressionIndex < 0 && totalGroups != 8) {
            return null;
        }
        if (compressionIndex >= 0 && totalGroups >= 8) {
            return null;
        }

        byte[] bytes = new byte[16];
        System.arraycopy(head, 0, bytes, 0, head.length);
        System.arraycopy(tail, 0, bytes, 16 - tail.length, tail.length);
        return bytes;
    }

    private static String[] splitIpv6Section(String section) {
        if (section == null || section.isBlank()) {
            return new String[0];
        }
        return section.split(":", -1);
    }

    private static byte[] parseIpv6Groups(String[] groups) {
        if (Arrays.stream(groups).anyMatch(String::isBlank)) {
            return null;
        }
        byte[] bytes = new byte[groups.length * 2];
        for (int i = 0; i < groups.length; i++) {
            String group = groups[i];
            if (group.length() > 4 || !group.chars().allMatch(ch -> Character.digit(ch, 16) >= 0)) {
                return null;
            }
            int value;
            try {
                value = Integer.parseInt(group, 16);
            } catch (NumberFormatException ex) {
                return null;
            }
            bytes[i * 2] = (byte) ((value >>> 8) & 0xff);
            bytes[i * 2 + 1] = (byte) (value & 0xff);
        }
        return bytes;
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
        int third = bytes[2] & 0xff;
        return first == 0
                || first == 10
                || first == 127
                || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 192 && second == 88 && third == 99)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113);
    }

    private static boolean isBlockedIpv6(byte[] bytes) {
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        int fourth = bytes[3] & 0xff;
        return (first & 0xfe) == 0xfc
                || (first == 0xfe && (second & 0xc0) == 0x80)
                || (first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8);
    }
}
