package com.joblens.analysis.provider;

/**
 * Provider-neutral boundary for fit analysis.
 *
 * <p>The domain must never depend on a vendor SDK type. Choosing a real runtime provider is a
 * separate decision from choosing hosting infrastructure, and it is deliberately deferred until the
 * evaluation harness has compared candidates on requirement-extraction accuracy, evidence
 * faithfulness, gap-versus-unknown classification, score stability, structured-output reliability,
 * latency and cost.
 *
 * <p>The {@code analyze} operation is intentionally absent until the versioned analysis schema
 * exists, so that the contract is defined by the schema rather than by whichever vendor lands first.
 */
public interface AnalysisProvider {

    /**
     * Stable identifier recorded in privacy-safe operational metrics and returned in analysis
     * metadata. Never includes credentials or endpoint hosts.
     */
    String id();

    /**
     * Whether serving a request sends document content outside this process. The application must
     * be able to run end to end with a provider that answers {@code false}.
     */
    boolean sendsContentOffHost();
}
