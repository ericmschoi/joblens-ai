package com.joblens.jobposting.extract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PageContentExtractorTest {

    private final PageContentExtractor extractor = new PageContentExtractor(JsonMapper.builder().build());

    private static String pageWith(String head, String body) {
        return "<html><head>" + head + "</head><body>" + body + "</body></html>";
    }

    @Test
    void prefersTheStructuredDataASitePublishesForSearchEngines() {
        String jsonLd = """
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "JobPosting",
                  "title": "Senior Backend Engineer",
                  "hiringOrganization": { "@type": "Organization", "name": "Acme Corp" },
                  "jobLocation": { "@type": "Place", "address": {
                      "addressLocality": "Toronto", "addressRegion": "ON" } },
                  "employmentType": "FULL_TIME",
                  "baseSalary": { "@type": "MonetaryAmount", "currency": "CAD",
                      "value": { "minValue": 120000, "maxValue": 150000, "unitText": "YEAR" } },
                  "description": "<h2>Required Qualifications</h2><ul><li>Strong Java</li><li>AWS</li></ul>"
                }
                </script>
                """;

        ExtractedPageContent content = extractor.extract(pageWith(jsonLd, "<p>Marketing fluff</p>"));

        assertThat(content.strategy()).isEqualTo(ExtractedPageContent.Strategy.JSON_LD);
        assertThat(content.title()).isEqualTo("Senior Backend Engineer");
        assertThat(content.company()).isEqualTo("Acme Corp");
        assertThat(content.location()).isEqualTo("Toronto, ON");
        assertThat(content.employmentType()).isEqualTo("Full time");
        assertThat(content.compensationText()).isEqualTo("CAD 120000 - 150000 per year");
        assertThat(content.text()).contains("Required Qualifications", "- Strong Java", "- AWS");
    }

    @Test
    void findsThePostingInsideAnAtGraphWrapper() {
        String jsonLd = """
                <script type="application/ld+json">
                { "@context": "https://schema.org", "@graph": [
                    { "@type": "Organization", "name": "Acme Corp" },
                    { "@type": "JobPosting", "title": "Platform Engineer",
                      "description": "<p>We need someone to run the platform and keep it healthy.</p>" } ] }
                </script>
                """;

        ExtractedPageContent content = extractor.extract(pageWith(jsonLd, "<p>fluff</p>"));

        assertThat(content.strategy()).isEqualTo(ExtractedPageContent.Strategy.JSON_LD);
        assertThat(content.title()).isEqualTo("Platform Engineer");
    }

    @Test
    void marksARemotePostingAsRemote() {
        String jsonLd = """
                <script type="application/ld+json">
                { "@type": "JobPosting", "title": "Backend Engineer",
                  "jobLocationType": "TELECOMMUTE",
                  "description": "<p>Work from anywhere in Canada on our payments platform.</p>" }
                </script>
                """;

        assertThat(extractor.extract(pageWith(jsonLd, "")).location()).isEqualTo("Remote");
    }

    @Test
    void fallsBackToTheMarkupWhenStructuredDataIsMalformed() {
        String broken = "<script type=\"application/ld+json\">{ not valid json </script>";
        String body = "<main><h2>Responsibilities</h2><ul><li>Build services</li></ul>"
                + "<h2>Required Qualifications</h2><ul><li>Strong Java</li></ul>"
                + "<p>" + "Filler to make this page long enough to look like content. ".repeat(6) + "</p></main>";

        ExtractedPageContent content = extractor.extract(pageWith(broken, body));

        assertThat(content.strategy()).isEqualTo(ExtractedPageContent.Strategy.GENERIC_HTML);
        assertThat(content.text()).contains("Responsibilities", "- Build services", "- Strong Java");
    }

    @Test
    void keepsEachListItemAsItsOwnLine() {
        String body = "<main><ul><li>Strong Java</li><li>Spring Boot</li><li>AWS</li></ul>"
                + "<p>" + "Padding so that this container is chosen as the content. ".repeat(6) + "</p></main>";

        String text = extractor.extract(pageWith("", body)).text();

        assertThat(text.lines().filter(line -> line.startsWith("- ")))
                .as("merging two bullets would merge two requirements")
                .hasSize(3);
    }

    @Test
    void leavesOutNavigationScriptsAndFooters() {
        String body = "<nav>Home Jobs About</nav><script>window.tracking = 1;</script>"
                + "<main><h2>Required Qualifications</h2><ul><li>Strong Java</li></ul>"
                + "<p>" + "Real posting content that should survive extraction. ".repeat(6) + "</p></main>"
                + "<footer>Copyright Acme</footer>";

        String text = extractor.extract(pageWith("", body)).text();

        assertThat(text).contains("Strong Java");
        assertThat(text).doesNotContain("window.tracking", "Home Jobs About", "Copyright Acme");
    }

    @Test
    void neverProducesMarkupThatCouldRunInABrowser() {
        String jsonLd = """
                <script type="application/ld+json">
                { "@type": "JobPosting", "title": "Engineer",
                  "description": "<p>Join us</p><img src=x onerror=alert(1)><script>alert(2)</script>" }
                </script>
                """;

        String text = extractor.extract(pageWith(jsonLd, "")).text();

        assertThat(text).doesNotContain("<", "onerror", "alert(");
    }
}
