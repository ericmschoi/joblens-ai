package com.joblens.jobposting.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.jobposting.JobPostingParser;
import com.joblens.jobposting.JobPostingTextNormalizer;
import com.joblens.jobposting.model.JobPosting;
import com.joblens.testsupport.AtsPageFixtures;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class AtsExtractionTest {

    private final PageContentExtractor extractor = new PageContentExtractor(JsonMapper.builder().build());
    private final JobPostingTextNormalizer normalizer = new JobPostingTextNormalizer();
    private final JobPostingParser parser = new JobPostingParser();

    private ExtractedPageContent read(String html, String url) {
        return extractor.extract(html, URI.create(url));
    }

    private JobPosting parse(ExtractedPageContent content) {
        return parser.parse(normalizer.normalize(content.text())).posting();
    }

    private static String htmlFor(String site) {
        return switch (site) {
            case "GREENHOUSE" -> AtsPageFixtures.greenhouse();
            case "LEVER" -> AtsPageFixtures.lever();
            case "ASHBY" -> AtsPageFixtures.ashby();
            case "WORKDAY" -> AtsPageFixtures.workday();
            default -> throw new IllegalArgumentException(site);
        };
    }

    private static String urlFor(String site) {
        return switch (site) {
            case "GREENHOUSE" -> AtsPageFixtures.GREENHOUSE_URL;
            case "LEVER" -> AtsPageFixtures.LEVER_URL;
            case "ASHBY" -> AtsPageFixtures.ASHBY_URL;
            case "WORKDAY" -> AtsPageFixtures.WORKDAY_URL;
            default -> throw new IllegalArgumentException(site);
        };
    }

    @ParameterizedTest
    @CsvSource({
            "GREENHOUSE, ATS_GREENHOUSE, Acmecorp",
            "LEVER, ATS_LEVER, Acme Corp",
            "ASHBY, ATS_ASHBY, Acme",
            "WORKDAY, ATS_WORKDAY, Acme"
    })
    void readsEachBoardWithItsOwnLayout(String site, ExtractedPageContent.Strategy strategy, String company) {
        ExtractedPageContent content = read(htmlFor(site), urlFor(site));

        assertThat(content.strategy()).isEqualTo(strategy);
        assertThat(content.title()).isEqualTo("Senior Backend Engineer");
        assertThat(content.company()).isEqualTo(company);
        assertThat(content.location()).contains("Toronto, ON");
    }

    @ParameterizedTest
    @CsvSource({"GREENHOUSE", "LEVER", "ASHBY", "WORKDAY"})
    void findsTheSameRequirementsOnEveryBoard(String site) {
        JobPosting posting = parse(read(htmlFor(site), urlFor(site)));

        assertThat(posting.requiredQualifications()).hasSize(3);
        assertThat(posting.preferredQualifications()).hasSize(1);
        assertThat(posting.responsibilities()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({"GREENHOUSE", "LEVER", "ASHBY", "WORKDAY"})
    void leavesTheBoardsFurnitureBehind(String site) {
        ExtractedPageContent content = read(htmlFor(site), urlFor(site));

        assertThat(content.text())
                .as("related jobs, the application form and the footer are not part of the posting")
                .doesNotContain("Other openings", "Staff Data Engineer", "Submit application",
                        "equal opportunity employer", "All jobs");
    }

    @Test
    void fallsBackToTheGenericReadingWhenABoardChangesItsMarkup() {
        String redesigned = AtsPageFixtures.greenhouse()
                .replace("id=\"content\"", "id=\"job-body-v2\"")
                .replace("class=\"app-title\"", "class=\"title-v2\"");

        ExtractedPageContent content = read(redesigned, AtsPageFixtures.GREENHOUSE_URL);

        assertThat(content.strategy())
                .as("a redesign must degrade to the generic reading, not return nothing")
                .isEqualTo(ExtractedPageContent.Strategy.GENERIC_HTML);
        assertThat(content.text()).contains("Strong Java and Spring Boot");
    }

    @Test
    void leavesNonAtsPagesToTheExistingStrategies() {
        String page = "<html><head><title>Careers</title></head><body><main>"
                + "<h2>Required Qualifications</h2><ul><li>Strong Java</li></ul><p>"
                + "Padding so that this container is treated as the page content. ".repeat(6)
                + "</p></main></body></html>";

        assertThat(read(page, "https://careers.example.com/jobs/1").strategy())
                .isEqualTo(ExtractedPageContent.Strategy.GENERIC_HTML);
    }

    @Test
    void anUnrenderedBoardShellYieldsNothingForTheCallerToAssess() {
        ExtractedPageContent content = read(AtsPageFixtures.unrenderedShell(), AtsPageFixtures.ASHBY_URL);

        assertThat(content.text().length())
                .as("there is no posting in the shell; the caller decides whether to render it")
                .isLessThan(200);
    }

    @Test
    void recognisesTheHostsEachBoardServesFrom() {
        assertThat(AtsSite.forUrl(URI.create("https://job-boards.greenhouse.io/acme/jobs/1")))
                .contains(AtsSite.GREENHOUSE);
        assertThat(AtsSite.forUrl(URI.create("https://jobs.eu.lever.co/acme/1"))).contains(AtsSite.LEVER);
        assertThat(AtsSite.forUrl(URI.create("https://acme.wd3.myworkdayjobs.com/en-US/careers")))
                .contains(AtsSite.WORKDAY);
        assertThat(AtsSite.forUrl(URI.create("https://careers.example.com/jobs/1"))).isEmpty();
    }
}
