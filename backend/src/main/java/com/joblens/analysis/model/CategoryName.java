package com.joblens.analysis.model;

import java.math.BigDecimal;

/**
 * The six rated categories and their weights.
 *
 * <p>The weights live here, in one place, because the total is the sum of these products and
 * nothing else. A weight that drifted out of sync with the documentation would change every score
 * silently, so the sum is asserted in a test.
 *
 * <p>{@code displayName} is product copy and is always English.
 */
public enum CategoryName {

    CORE_TECHNICAL_STACK("Core Technical Stack", "0.25"),
    ROLE_AND_RESPONSIBILITY_ALIGNMENT("Role & Responsibility Alignment", "0.20"),
    EXPERIENCE_EVIDENCE("Experience Evidence", "0.15"),
    SENIORITY_AND_OWNERSHIP("Seniority & Ownership", "0.15"),
    REQUIRED_QUALIFICATION_COVERAGE("Required Qualification Coverage", "0.15"),
    DOMAIN_AND_OPERATING_ENVIRONMENT("Domain & Operating Environment", "0.10");

    private final String displayName;
    private final BigDecimal weight;

    CategoryName(String displayName, String weight) {
        this.displayName = displayName;
        this.weight = new BigDecimal(weight);
    }

    public String displayName() {
        return displayName;
    }

    public BigDecimal weight() {
        return weight;
    }
}
