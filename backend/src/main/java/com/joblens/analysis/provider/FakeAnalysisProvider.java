package com.joblens.analysis.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic in-process provider used for local development and every automated test.
 *
 * <p>It is the default so that the application starts and runs without an API key and without any
 * outbound AI traffic. Enabling a real provider is an explicit configuration change.
 */
@Component
@ConditionalOnProperty(name = "joblens.analysis.provider", havingValue = "fake", matchIfMissing = true)
public class FakeAnalysisProvider implements AnalysisProvider {

    public static final String ID = "fake";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean sendsContentOffHost() {
        return false;
    }
}
