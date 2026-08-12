package com.joblens.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.scoring.model.ScoreLabel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The curve and the bands, pinned at the points where a reader would notice a change. */
class ScoringRubricTest {

    @ParameterizedTest
    @CsvSource({
            "0.00, 0.0", "0.20, 1.0", "0.40, 2.0", "0.60, 3.0", "0.80, 4.0", "0.95, 4.7", "1.00, 5.0"
    })
    void theAnchorsAreExactlyWhereTheRubricSaysTheyAre(String coverage, String expected) {
        assertThat(ScoringRubric.scoreForCoverage(new BigDecimal(coverage)))
                .isEqualByComparingTo(expected);
    }

    @Test
    void interpolatesBetweenAnchors() {
        // 0.7752 sits between the 0.60 and 0.80 anchors: 3.0 + (0.1752 / 0.20) = 3.876.
        assertThat(ScoringRubric.scoreForCoverage(new BigDecimal("0.7752")))
                .isEqualByComparingTo("3.9");
    }

    @Test
    void coverageOutsideTheRangeIsClamped() {
        assertThat(ScoringRubric.scoreForCoverage(new BigDecimal("-0.5"))).isEqualByComparingTo("0.0");
        assertThat(ScoringRubric.scoreForCoverage(new BigDecimal("1.5"))).isEqualByComparingTo("5.0");
    }

    @ParameterizedTest
    @CsvSource({
            "0.0, Poor Match", "1.9, Poor Match",
            "2.0, Weak Match", "2.9, Weak Match",
            "3.0, Moderate Match", "3.4, Moderate Match",
            "3.5, Good Match", "3.9, Good Match",
            "4.0, Strong Match", "4.4, Strong Match",
            "4.5, Excellent Match", "5.0, Excellent Match"
    })
    void everyLabelBoundaryFallsWhereTheGuideSaysItDoes(String score, String label) {
        assertThat(ScoreLabel.forScore(new BigDecimal(score)).displayName()).isEqualTo(label);
    }

    @Test
    void roundingIsHalfUpToOneDecimalPlace() {
        assertThat(ScoringRubric.round(new BigDecimal("3.44"))).isEqualByComparingTo("3.4");
        assertThat(ScoringRubric.round(new BigDecimal("3.45"))).isEqualByComparingTo("3.5");
        assertThat(ScoringRubric.round(new BigDecimal("3.4499"))).isEqualByComparingTo("3.4");
    }

    @Test
    void theCaveatTravelsWithTheGuide() {
        assertThat(ScoreLabel.CAVEAT)
                .contains("explicitly documented in the resume")
                .contains("required qualifications are weighted more heavily");
    }
}
