package com.joblens.analysis.model;

/**
 * How attractive the opportunity might be, which is a different question from how well the
 * candidate fits it.
 *
 * <p>Kept in its own type, and deliberately not reachable from the scoring package, so that a
 * prestigious employer or a generous salary cannot inflate a fit score. An architecture test
 * enforces the separation.
 */
public record OpportunityValue(
        Assessment careerGrowth,
        Assessment compensation,
        Assessment companyOutlook) {}
