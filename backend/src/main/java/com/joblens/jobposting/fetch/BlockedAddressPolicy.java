package com.joblens.jobposting.fetch;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Decides which IP addresses the server is allowed to connect to on a user's behalf.
 *
 * <p>The threat is server-side request forgery: a URL that looks ordinary but resolves to something
 * only this server can reach — a database on the private network, a sidecar on loopback, or the
 * cloud metadata endpoint that hands out credentials. The check is on the resolved address, because
 * the hostname says nothing about where it points.
 *
 * <p>Addresses that embed an IPv4 address inside an IPv6 one are unwrapped and re-checked. Without
 * that, {@code ::ffff:127.0.0.1} would pass as an unremarkable IPv6 address.
 */
public final class BlockedAddressPolicy {

    /**
     * Endpoints that hand out cloud credentials. Every one of these is already covered by a range
     * below; they are named as well so the intent survives a future edit to the ranges.
     */
    private static final Set<String> KNOWN_METADATA_ENDPOINTS = Set.of(
            "169.254.169.254",   // AWS, Azure, GCP, DigitalOcean
            "169.254.170.2",     // AWS ECS task metadata
            "100.100.100.200",   // Alibaba Cloud
            "192.0.0.192",       // Oracle Cloud
            "fd00:ec2::254");    // AWS IMDS over IPv6

    /**
     * Loopback is blocked in production and allowed only where a test needs to serve a page from a
     * local port. It is a constructor argument rather than a configuration property so that no
     * deployment can switch it on by editing a file.
     */
    private final boolean allowLoopback;

    public BlockedAddressPolicy(boolean allowLoopback) {
        this.allowLoopback = allowLoopback;
    }

    public boolean isBlocked(InetAddress address) {
        InetAddress effective = unwrapEmbeddedIpv4(address);

        if (effective.isLoopbackAddress()) {
            return !allowLoopback;
        }
        if (effective.isAnyLocalAddress()
                || effective.isLinkLocalAddress()
                || effective.isSiteLocalAddress()
                || effective.isMulticastAddress()) {
            return true;
        }
        if (KNOWN_METADATA_ENDPOINTS.contains(effective.getHostAddress())) {
            return true;
        }
        return effective instanceof Inet4Address ipv4
                ? isBlockedIpv4(ipv4.getAddress())
                : isBlockedIpv6((Inet6Address) effective);
    }

    private static boolean isBlockedIpv4(byte[] octets) {
        int first = octets[0] & 0xFF;
        int second = octets[1] & 0xFF;
        int third = octets[2] & 0xFF;

        // 100.64.0.0/10, carrier-grade NAT: reachable inside many hosting networks.
        if (first == 100 && second >= 64 && second <= 127) {
            return true;
        }
        // 192.0.0.0/24 IETF protocol assignments, 192.0.2.0/24 documentation.
        if (first == 192 && second == 0 && (third == 0 || third == 2)) {
            return true;
        }
        // 198.18.0.0/15 benchmarking, 198.51.100.0/24 documentation.
        if (first == 198 && (second == 18 || second == 19)) {
            return true;
        }
        if (first == 198 && second == 51 && third == 100) {
            return true;
        }
        // 203.0.113.0/24 documentation.
        if (first == 203 && second == 0 && third == 113) {
            return true;
        }
        // 240.0.0.0/4 reserved, and 255.255.255.255 broadcast.
        return first >= 240;
    }

    private boolean isBlockedIpv6(Inet6Address address) {
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xFF;

        // fc00::/7, unique local addresses.
        if ((first & 0xFE) == 0xFC) {
            return true;
        }
        // 2002::/16, 6to4: the embedded IPv4 address decides.
        if (first == 0x20 && (bytes[1] & 0xFF) == 0x02) {
            return isBlockedEmbeddedIpv4(bytes[2], bytes[3], bytes[4], bytes[5]);
        }
        // 64:ff9b::/96, NAT64: likewise.
        if (bytes[0] == 0x00 && bytes[1] == 0x64 && (bytes[2] & 0xFF) == 0xFF && (bytes[3] & 0xFF) == 0x9B) {
            return isBlockedEmbeddedIpv4(bytes[12], bytes[13], bytes[14], bytes[15]);
        }
        return false;
    }

    /**
     * Re-runs the whole policy on the address carried inside an IPv6 one.
     *
     * <p>Checking only the numeric ranges here would miss loopback, private and link-local, because
     * those are properties of the address object rather than of the octets. That gap made
     * {@code 2002:7f00:0001::} — 127.0.0.1 in 6to4 clothing — reachable.
     */
    private boolean isBlockedEmbeddedIpv4(byte first, byte second, byte third, byte fourth) {
        try {
            return isBlocked(InetAddress.getByAddress(new byte[] {first, second, third, fourth}));
        } catch (UnknownHostException e) {
            return true;
        }
    }

    /** Turns {@code ::ffff:a.b.c.d} into {@code a.b.c.d} so IPv4 rules apply to it. */
    private static InetAddress unwrapEmbeddedIpv4(InetAddress address) {
        if (!(address instanceof Inet6Address ipv6)) {
            return address;
        }
        byte[] bytes = ipv6.getAddress();
        boolean ipv4Mapped = true;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                ipv4Mapped = false;
                break;
            }
        }
        if (!ipv4Mapped || (bytes[10] & 0xFF) != 0xFF || (bytes[11] & 0xFF) != 0xFF) {
            return address;
        }
        try {
            return InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
        } catch (UnknownHostException e) {
            return address;
        }
    }
}
