package com.joblens.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.resume.model.DateRange;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DateRangeParserTest {

    @Test
    void readsAMonthAndYearRangeThatIsStillOpen() {
        DateRange range = DateRangeParser.findIn("Senior Engineer   Mar 2021 - Present").orElseThrow();

        assertThat(range.startYearMonth()).isEqualTo("2021-03");
        assertThat(range.endYearMonth()).isNull();
        assertThat(range.current()).isTrue();
        assertThat(range.parseConfidence()).isEqualTo(DateRange.Confidence.HIGH);
    }

    @Test
    void readsAClosedMonthAndYearRange() {
        DateRange range = DateRangeParser.findIn("Jul 2018 - Feb 2021").orElseThrow();

        assertThat(range.startYearMonth()).isEqualTo("2018-07");
        assertThat(range.endYearMonth()).isEqualTo("2021-02");
        assertThat(range.current()).isFalse();
        assertThat(range.parseConfidence()).isEqualTo(DateRange.Confidence.HIGH);
    }

    @Test
    void readsSpelledOutMonthsAndTheWordTo() {
        DateRange range = DateRangeParser.findIn("September 2014 to April 2018").orElseThrow();

        assertThat(range.startYearMonth()).isEqualTo("2014-09");
        assertThat(range.endYearMonth()).isEqualTo("2018-04");
    }

    @Test
    void readsNumericMonths() {
        DateRange range = DateRangeParser.findIn("03/2021 - 05/2023").orElseThrow();

        assertThat(range.startYearMonth()).isEqualTo("2021-03");
        assertThat(range.endYearMonth()).isEqualTo("2023-05");
        assertThat(range.parseConfidence()).isEqualTo(DateRange.Confidence.HIGH);
    }

    @Test
    void marksYearOnlyRangesAsLowConfidenceBecauseTheDurationIsAmbiguous() {
        DateRange range = DateRangeParser.findIn("2019 - 2022").orElseThrow();

        assertThat(range.startYearMonth()).isEqualTo("2019-01");
        assertThat(range.endYearMonth()).isEqualTo("2022-01");
        assertThat(range.parseConfidence())
                .as("2019-2022 could be 13 months or 35, so the range must not claim precision")
                .isEqualTo(DateRange.Confidence.LOW);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Senior Software Engineer",
            "Built REST APIs in Java for a logistics platform",
            "Toronto, ON"
    })
    void findsNothingInLinesWithoutADateRange(String line) {
        assertThat(DateRangeParser.findIn(line)).isEmpty();
    }

    @Test
    void reportsTheMatchedTextSoItCanBeStrippedFromAHeading() {
        Optional<String> raw = DateRangeParser.findRawIn("Senior Engineer, Northwind   Mar 2021 - Present");

        assertThat(raw).contains("Mar 2021 - Present");
    }

    @Test
    void keepsTheOriginalWordingInRawText() {
        DateRange range = DateRangeParser.findIn("Mar 2021 - Present").orElseThrow();

        assertThat(range.rawText()).isEqualTo("Mar 2021 - Present");
    }
}
