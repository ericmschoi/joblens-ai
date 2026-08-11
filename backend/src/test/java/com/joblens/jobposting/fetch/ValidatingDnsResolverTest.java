package com.joblens.jobposting.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

/**
 * The last line of defence before a socket opens.
 *
 * <p>Validating the URL up front cannot stop a name from being re-pointed at a private address in
 * the moment between that check and the connection. Because the connection manager connects to
 * whatever this resolver hands back, validating here is what makes the earlier check hold.
 */
class ValidatingDnsResolverTest {

    private static HostResolver resolvingTo(String... literals) {
        return host -> {
            InetAddress[] addresses = new InetAddress[literals.length];
            for (int i = 0; i < literals.length; i++) {
                addresses[i] = InetAddress.getByName(literals[i]);
            }
            return addresses;
        };
    }

    private static ValidatingDnsResolver resolver(String... literals) {
        return new ValidatingDnsResolver(new BlockedAddressPolicy(false), resolvingTo(literals));
    }

    @Test
    void passesThroughOrdinaryPublicAddresses() throws UnknownHostException {
        assertThat(resolver("93.184.216.34").resolve("jobs.example.com")).hasSize(1);
    }

    @Test
    void refusesANameThatNowPointsAtAPrivateAddress() {
        assertThatThrownBy(() -> resolver("10.0.0.7").resolve("rebound.example.com"))
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void refusesANameThatPointsAtBothAPublicAndAPrivateAddress() {
        assertThatThrownBy(() -> resolver("93.184.216.34", "169.254.169.254").resolve("split.example.com"))
                .as("connecting to the allowed one would leave the other reachable on a retry")
                .isInstanceOf(UnknownHostException.class);
    }

    @Test
    void saysNothingUsefulAboutWhatItRefused() {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> resolver("10.0.0.7").resolve("rebound.example.com"));

        assertThat(thrown.getMessage()).doesNotContain("10.0.0.7");
    }

    @Test
    void canonicalHostnameLookupIsSubjectToTheSameRules() {
        assertThatThrownBy(() -> resolver("192.168.1.1").resolveCanonicalHostname("router.example.com"))
                .isInstanceOf(UnknownHostException.class);
    }
}
