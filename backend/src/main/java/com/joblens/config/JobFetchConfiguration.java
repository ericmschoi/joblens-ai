package com.joblens.config;

import com.joblens.jobposting.fetch.BlockedAddressPolicy;
import com.joblens.jobposting.fetch.HostResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for outbound page fetching.
 *
 * <p>{@code allowLoopback} is passed as {@code false} here and is not readable from configuration.
 * Tests that need to serve a page from a local port construct their own policy; no deployment can
 * open that door by editing a properties file.
 */
@Configuration
class JobFetchConfiguration {

    @Bean
    BlockedAddressPolicy blockedAddressPolicy() {
        return new BlockedAddressPolicy(false);
    }

    @Bean
    HostResolver hostResolver() {
        return HostResolver.system();
    }
}
