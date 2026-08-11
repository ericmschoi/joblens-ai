package com.joblens.jobposting.fetch;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The address matrix that decides whether this endpoint can be used to reach the inside of the
 * network it runs in. Literals only, so nothing here depends on DNS.
 */
class BlockedAddressPolicyTest {

    private final BlockedAddressPolicy policy = new BlockedAddressPolicy(false);

    private static InetAddress address(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1", "127.1.2.3", "0.0.0.0",
            "10.0.0.1", "172.16.5.4", "172.31.255.254", "192.168.1.1",
            "169.254.1.1",
            "100.64.0.1", "100.127.255.254",
            "192.0.0.1", "192.0.2.5", "198.18.0.1", "198.19.255.255",
            "198.51.100.7", "203.0.113.9",
            "224.0.0.1", "239.255.255.250",
            "240.0.0.1", "255.255.255.255"
    })
    void blocksIpv4AddressesThatAreNotThePublicInternet(String literal) throws UnknownHostException {
        assertThat(policy.isBlocked(address(literal))).as(literal).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::1", "::", "fe80::1", "fc00::1", "fd12:3456:789a::1", "ff02::1"
    })
    void blocksIpv6AddressesThatAreNotThePublicInternet(String literal) throws UnknownHostException {
        assertThat(policy.isBlocked(address(literal))).as(literal).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "169.254.169.254",   // AWS, Azure, GCP
            "169.254.170.2",     // ECS task metadata
            "100.100.100.200",   // Alibaba
            "192.0.0.192",       // Oracle
            "fd00:ec2::254"      // IMDS over IPv6
    })
    void blocksCloudMetadataEndpoints(String literal) throws UnknownHostException {
        assertThat(policy.isBlocked(address(literal))).as(literal).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:169.254.169.254"
    })
    void seesThroughIpv4AddressesWrappedInIpv6(String literal) throws UnknownHostException {
        assertThat(policy.isBlocked(address(literal)))
                .as("%s is loopback or private wearing an IPv6 hat", literal)
                .isTrue();
    }

    @Test
    void seesThroughSixToFourAndNat64Encodings() throws UnknownHostException {
        assertThat(policy.isBlocked(address("2002:7f00:0001::")))
                .as("2002::/16 with 127.0.0.1 embedded")
                .isTrue();
        assertThat(policy.isBlocked(address("64:ff9b::a00:1")))
                .as("NAT64 with 10.0.0.1 embedded")
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.216.34", "8.8.8.8", "1.1.1.1", "2606:2800:220:1:248:1893:25c8:1946"})
    void allowsOrdinaryPublicAddresses(String literal) throws UnknownHostException {
        assertThat(policy.isBlocked(address(literal))).as(literal).isFalse();
    }

    @Test
    void loopbackIsOnlyReachableWhenATestExplicitlyAsksForIt() throws UnknownHostException {
        assertThat(new BlockedAddressPolicy(false).isBlocked(address("127.0.0.1"))).isTrue();
        assertThat(new BlockedAddressPolicy(true).isBlocked(address("127.0.0.1"))).isFalse();
    }

    @Test
    void allowingLoopbackDoesNotAlsoAllowPrivateOrMetadataAddresses() throws UnknownHostException {
        BlockedAddressPolicy permissive = new BlockedAddressPolicy(true);

        assertThat(permissive.isBlocked(address("10.0.0.1"))).isTrue();
        assertThat(permissive.isBlocked(address("169.254.169.254"))).isTrue();
    }
}
