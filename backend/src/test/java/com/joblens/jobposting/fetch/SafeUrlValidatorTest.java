package com.joblens.jobposting.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import com.joblens.testsupport.TestProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SafeUrlValidatorTest {

    private static final String PUBLIC_IP = "93.184.216.34";

    /** Resolution is stubbed so the rules are tested, not whatever public DNS returns today. */
    private static HostResolver resolvingTo(String... literals) {
        return host -> {
            InetAddress[] addresses = new InetAddress[literals.length];
            for (int i = 0; i < literals.length; i++) {
                addresses[i] = InetAddress.getByName(literals[i]);
            }
            return addresses;
        };
    }

    private static SafeUrlValidator validatorResolving(String... literals) {
        return new SafeUrlValidator(TestProperties.defaults(), new BlockedAddressPolicy(false),
                resolvingTo(literals));
    }

    private static ErrorCode codeOf(Throwable thrown) {
        return ((ApiException) thrown).errorCode();
    }

    @Test
    void acceptsAnOrdinaryPublicHttpsUrl() {
        assertThatCode(() -> validatorResolving(PUBLIC_IP).validate("https://jobs.example.com/roles/42"))
                .doesNotThrowAnyException();
    }

    @Test
    void returnsTheAddressesItValidated() {
        SafeUrlValidator.ValidatedUrl validated =
                validatorResolving(PUBLIC_IP).validate("https://jobs.example.com/roles/42");

        assertThat(validated.host()).isEqualTo("jobs.example.com");
        assertThat(validated.addresses()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "ftp://example.com/jobs",
            "gopher://example.com/",
            "jar:http://example.com!/",
            "data:text/html,<h1>hi</h1>"
    })
    void refusesEverySchemeExceptHttpAndHttps(String url) {
        assertThatThrownBy(() -> validatorResolving(PUBLIC_IP).validate(url))
                .isInstanceOf(ApiException.class)
                .extracting(SafeUrlValidatorTest::codeOf)
                .isIn(ErrorCode.URL_SCHEME_NOT_ALLOWED, ErrorCode.URL_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://user:password@jobs.example.com/roles/42",
            "https://jobs.example.com@169.254.169.254/latest/meta-data/",
            "https://user@internal.example.com/"
    })
    void refusesUrlsCarryingCredentialsOrADisguisedHost(String url) {
        assertThatThrownBy(() -> validatorResolving(PUBLIC_IP).validate(url))
                .isInstanceOf(ApiException.class)
                .extracting(SafeUrlValidatorTest::codeOf)
                .isIn(ErrorCode.URL_INVALID, ErrorCode.URL_BLOCKED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://jobs.example.com:22/x", "http://jobs.example.com:6379/x",
            "http://jobs.example.com:8080/x"})
    void refusesPortsOutsideTheAllowedSet(String url) {
        assertThatThrownBy(() -> validatorResolving(PUBLIC_IP).validate(url))
                .isInstanceOf(ApiException.class)
                .extracting(SafeUrlValidatorTest::codeOf)
                .isEqualTo(ErrorCode.URL_BLOCKED);
    }

    @Test
    void allowsTheStandardWebPortsWrittenOutInFull() {
        assertThatCode(() -> validatorResolving(PUBLIC_IP).validate("https://jobs.example.com:443/x"))
                .doesNotThrowAnyException();
        assertThatCode(() -> validatorResolving(PUBLIC_IP).validate("http://jobs.example.com:80/x"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/admin",
            "http://localhost/admin",
            "http://10.0.0.5/",
            "http://169.254.169.254/latest/meta-data/",
            "http://[::1]/",
            "http://[fd00:ec2::254]/latest/meta-data/",
            "http://[::ffff:127.0.0.1]/"
    })
    void refusesAddressesOnlyThisServerCouldReach(String url) {
        SafeUrlValidator validator = new SafeUrlValidator(TestProperties.defaults(),
                new BlockedAddressPolicy(false), HostResolver.system());

        assertThatThrownBy(() -> validator.validate(url))
                .isInstanceOf(ApiException.class)
                .extracting(SafeUrlValidatorTest::codeOf)
                .isEqualTo(ErrorCode.URL_BLOCKED);
    }

    @Test
    void refusesAHostThatResolvesToBothAPublicAndAPrivateAddress() {
        assertThatThrownBy(() -> validatorResolving(PUBLIC_IP, "10.0.0.7")
                .validate("https://split-horizon.example.com/jobs"))
                .as("connecting to the public one now says nothing about the next connection")
                .isInstanceOf(ApiException.class)
                .extracting(SafeUrlValidatorTest::codeOf)
                .isEqualTo(ErrorCode.URL_BLOCKED);
    }

    @Test
    void refusesAHostThatDoesNotResolve() {
        HostResolver failing = host -> {
            throw new UnknownHostException(host);
        };
        SafeUrlValidator validator = new SafeUrlValidator(TestProperties.defaults(),
                new BlockedAddressPolicy(false), failing);

        assertThatThrownBy(() -> validator.validate("https://nope.example.com/"))
                .isInstanceOf(ApiException.class)
                .extracting(SafeUrlValidatorTest::codeOf)
                .isEqualTo(ErrorCode.URL_BLOCKED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not a url", "/relative/path", "//example.com/jobs", "https://"})
    void refusesInputThatIsNotAnAbsoluteUrl(String url) {
        assertThatThrownBy(() -> validatorResolving(PUBLIC_IP).validate(url))
                .isInstanceOf(ApiException.class)
                .extracting(SafeUrlValidatorTest::codeOf)
                .isIn(ErrorCode.URL_INVALID, ErrorCode.URL_SCHEME_NOT_ALLOWED);
    }

    @Test
    void tellsTheClientNothingAboutWhyAnAddressWasRefused() {
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> validatorResolving("10.0.0.7").validate("https://internal.example.com/"));

        assertThat(((ApiException) thrown).detail())
                .as("a message naming the address would turn this endpoint into a network scanner")
                .doesNotContain("10.0.0.7")
                .isEqualTo("This address cannot be fetched.");
    }
}
