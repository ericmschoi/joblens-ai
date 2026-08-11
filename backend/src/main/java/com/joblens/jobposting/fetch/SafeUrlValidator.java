package com.joblens.jobposting.fetch;

import com.joblens.config.JoblensProperties;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The gate every outbound URL passes through, including each redirect hop.
 *
 * <p>Rejections are deliberately uninformative to the caller: the client is told the address cannot
 * be fetched, never why. "Blocked because it resolves to 10.0.4.19" would turn this endpoint into a
 * network scanner for anyone who asked it enough questions.
 */
@Component
public class SafeUrlValidator {

    private static final Logger LOG = LoggerFactory.getLogger(SafeUrlValidator.class);
    private static final List<String> ALLOWED_SCHEMES = List.of("http", "https");
    private static final String BLOCKED_MESSAGE = "This address cannot be fetched.";

    private final JoblensProperties.JobFetch limits;
    private final BlockedAddressPolicy blockedAddresses;
    private final HostResolver resolver;

    public SafeUrlValidator(JoblensProperties properties, BlockedAddressPolicy blockedAddresses,
            HostResolver resolver) {
        this.limits = properties.jobFetch();
        this.blockedAddresses = blockedAddresses;
        this.resolver = resolver;
    }

    /** @param rawUrl a URL from the user, or a {@code Location} header from a redirect */
    public ValidatedUrl validate(String rawUrl) {
        URI uri = parse(rawUrl);

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new ApiException(ErrorCode.URL_SCHEME_NOT_ALLOWED,
                    "Only http and https links can be opened.");
        }
        if (uri.getUserInfo() != null || uri.getRawAuthority() == null || uri.getRawAuthority().contains("@")) {
            // Credentials in a URL are both a smell and a classic way to disguise the real host.
            throw new ApiException(ErrorCode.URL_INVALID, "This link must not contain a username or password.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(ErrorCode.URL_INVALID, "This link does not contain a valid host name.");
        }
        int port = uri.getPort();
        if (port != -1 && !limits.allowedPorts().contains(port)) {
            throw new ApiException(ErrorCode.URL_BLOCKED, BLOCKED_MESSAGE);
        }

        List<InetAddress> addresses = resolveAndValidate(host);
        return new ValidatedUrl(uri, host, addresses);
    }

    private URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ApiException(ErrorCode.URL_INVALID, "No link was provided.");
        }
        try {
            URI uri = new URI(rawUrl.strip());
            if (!uri.isAbsolute()) {
                throw new ApiException(ErrorCode.URL_INVALID,
                        "This link is incomplete. Include the full address, starting with https://.");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new ApiException(ErrorCode.URL_INVALID, "This link could not be read as a web address.", e);
        }
    }

    private List<InetAddress> resolveAndValidate(String host) {
        String bare = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;

        InetAddress[] resolved;
        try {
            resolved = resolver.resolve(bare);
        } catch (UnknownHostException e) {
            throw new ApiException(ErrorCode.URL_BLOCKED, BLOCKED_MESSAGE, e);
        }
        if (resolved.length == 0) {
            throw new ApiException(ErrorCode.URL_BLOCKED, BLOCKED_MESSAGE);
        }

        // Every address, not just the first. A name that resolves to one public and one private
        // address must not be reachable through the private one on a later connection attempt.
        for (InetAddress address : resolved) {
            if (blockedAddresses.isBlocked(address)) {
                LOG.warn("code={} reason=blocked-address", ErrorCode.URL_BLOCKED);
                throw new ApiException(ErrorCode.URL_BLOCKED, BLOCKED_MESSAGE);
            }
        }
        return List.copyOf(Arrays.asList(resolved));
    }

    /** A URL that passed every check, with the addresses it resolved to at the time. */
    public record ValidatedUrl(URI uri, String host, List<InetAddress> addresses) {

        public ValidatedUrl {
            addresses = List.copyOf(addresses);
        }
    }
}
