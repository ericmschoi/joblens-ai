package com.joblens.jobposting.fetch;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.apache.hc.client5.http.DnsResolver;

/**
 * The HTTP client's own name resolution, filtered through the same address policy.
 *
 * <p>Validating the URL up front is not sufficient on its own. Between that check and the actual
 * connection, a name can be re-pointed at a private address — DNS rebinding. Because the connection
 * manager connects to whatever this resolver returns, validating here means the socket can only ever
 * open to an address that passed.
 */
final class ValidatingDnsResolver implements DnsResolver {

    private final BlockedAddressPolicy blockedAddresses;
    private final HostResolver delegate;

    ValidatingDnsResolver(BlockedAddressPolicy blockedAddresses, HostResolver delegate) {
        this.blockedAddresses = blockedAddresses;
        this.delegate = delegate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] resolved = delegate.resolve(host);
        InetAddress[] permitted = Arrays.stream(resolved)
                .filter(address -> !blockedAddresses.isBlocked(address))
                .toArray(InetAddress[]::new);

        if (permitted.length < resolved.length) {
            // Refuse the name outright rather than quietly connecting to whichever address happens
            // to be allowed. A name pointing at both is not a name this server should follow.
            throw new UnknownHostException("blocked-address");
        }
        return permitted;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        InetAddress[] permitted = resolve(host);
        if (permitted.length == 0) {
            return host;
        }
        String canonical = permitted[0].getCanonicalHostName();
        return canonical != null && !canonical.isBlank() ? canonical : host;
    }
}
