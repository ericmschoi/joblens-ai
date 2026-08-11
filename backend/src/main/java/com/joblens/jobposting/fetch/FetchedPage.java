package com.joblens.jobposting.fetch;

/**
 * A page that was fetched successfully within every limit.
 *
 * @param finalUrl the address after following redirects, each of which was re-validated
 * @param statusCode the status of the final response
 * @param contentType the declared type, already checked against the allowlist
 * @param body the response body, guaranteed to be within the configured size cap
 * @param redirectCount how many hops were followed
 * @param fetchMs wall-clock time spent, for the review screen and for operational metrics
 */
public record FetchedPage(
        String finalUrl,
        int statusCode,
        String contentType,
        String body,
        int redirectCount,
        long fetchMs) {}
