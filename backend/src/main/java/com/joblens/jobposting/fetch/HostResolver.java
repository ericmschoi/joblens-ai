package com.joblens.jobposting.fetch;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Name resolution, behind an interface so the SSRF rules can be tested without depending on what
 * public DNS happens to return today.
 */
@FunctionalInterface
public interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;

    static HostResolver system() {
        return InetAddress::getAllByName;
    }
}
