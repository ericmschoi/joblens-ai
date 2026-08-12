package com.joblens.analysis.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CategoryWeightsTest {

    @Test
    void theSixWeightsAddUpToExactlyOne() {
        BigDecimal total = Arrays.stream(CategoryName.values())
                .map(CategoryName::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(total)
                .as("a weight that drifted would change every score silently")
                .isEqualByComparingTo("1.00");
    }

    @Test
    void everyCategoryHasEnglishDisplayCopy() {
        assertThat(Arrays.stream(CategoryName.values()).map(CategoryName::displayName))
                .containsExactly("Core Technical Stack", "Role & Responsibility Alignment",
                        "Experience Evidence", "Seniority & Ownership",
                        "Required Qualification Coverage", "Domain & Operating Environment");
    }
}
