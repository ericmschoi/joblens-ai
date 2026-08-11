package com.joblens.testsupport;

import com.joblens.config.JoblensProperties;
import com.joblens.jobposting.JobPostingExtractionService;
import com.joblens.jobposting.JobPostingParser;
import com.joblens.jobposting.JobPostingTextNormalizer;
import com.joblens.jobposting.extract.PageAccessAssessor;
import com.joblens.jobposting.extract.PageContentExtractor;
import com.joblens.jobposting.fetch.BlockedAddressPolicy;
import com.joblens.jobposting.fetch.HostResolver;
import com.joblens.jobposting.fetch.SafeHttpFetcher;
import com.joblens.jobposting.fetch.SafeUrlValidator;
import tools.jackson.databind.json.JsonMapper;

/** Assembles the job-posting pipeline for tests that need it without a Spring context. */
public final class JobPostingServices {

    private JobPostingServices() {}

    /** For tests that only paste text. The fetch collaborators are wired but never exercised. */
    public static JobPostingExtractionService pasteOnly(JoblensProperties properties) {
        return build(properties, HostResolver.system(), false);
    }

    /**
     * For tests that serve a page from a local port.
     *
     * <p>Loopback is opened through the policy's constructor, which is the only way it can be
     * opened. Production wiring passes {@code false} and has no property that changes it.
     */
    public static JobPostingExtractionService reachingLoopback(JoblensProperties properties) {
        return build(properties, HostResolver.system(), true);
    }

    private static JobPostingExtractionService build(JoblensProperties properties, HostResolver resolver,
            boolean allowLoopback) {
        BlockedAddressPolicy addresses = new BlockedAddressPolicy(allowLoopback);
        SafeUrlValidator urlValidator = new SafeUrlValidator(properties, addresses, resolver);
        SafeHttpFetcher fetcher = new SafeHttpFetcher(properties, urlValidator, addresses, resolver);

        return new JobPostingExtractionService(
                properties,
                new JobPostingTextNormalizer(),
                new JobPostingParser(),
                urlValidator,
                fetcher,
                new PageContentExtractor(JsonMapper.builder().build()),
                new PageAccessAssessor());
    }
}
