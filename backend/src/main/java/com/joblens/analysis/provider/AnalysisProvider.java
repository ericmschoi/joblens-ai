package com.joblens.analysis.provider;

import com.joblens.analysis.AnalysisInput;

/**
 * Provider-neutral boundary for fit analysis.
 *
 * <p>Providers return raw JSON text rather than typed objects. That is what a real model returns,
 * and putting the parse and the validation on this side of the boundary means an adapter cannot
 * quietly hand the domain something that never passed the contract.
 *
 * <p>The domain must never depend on a vendor SDK type. Choosing a runtime provider is a separate
 * decision from choosing hosting, and it stays deferred until the evaluation harness has compared
 * candidates on requirement-extraction accuracy, evidence faithfulness, gap-versus-unknown
 * classification, score stability, structured-output reliability, latency and cost.
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

    /**
     * @return JSON matching the analysis draft contract. It is validated before use; a provider is
     *         not trusted to have produced something well formed.
     */
    String analyze(AnalysisInput input, String systemPrompt, String userPrompt);
}
