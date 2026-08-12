package com.joblens.testsupport;

/**
 * Synthetic pages modelled on the structure each applicant tracking system uses.
 *
 * <p>Written by hand rather than captured from live boards: a saved third-party page is someone
 * else's content, and a test that depends on a live URL is not a test. What these pin down is that
 * the selectors find the description and leave the board chrome behind.
 */
public final class AtsPageFixtures {

    private AtsPageFixtures() {}

    public static final String GREENHOUSE_URL = "https://boards.greenhouse.io/acmecorp/jobs/4012345";
    public static final String LEVER_URL = "https://jobs.lever.co/acme-corp/8f2c1d90-1111-4a2b";
    public static final String ASHBY_URL = "https://jobs.ashbyhq.com/acme/9c1f-4d2a-bb31";
    public static final String WORKDAY_URL =
            "https://acme.wd1.myworkdayjobs.com/en-US/External/job/Toronto/Senior-Backend-Engineer_R-1234";

    /** The description body every fixture wraps in its own board chrome. */
    private static final String DESCRIPTION = """
            <p>Acme is building payment infrastructure used by thousands of businesses, and we are
            hiring an engineer to help us keep it fast and dependable as it grows.</p>
            <h3>Responsibilities</h3>
            <ul>
              <li>Design and build backend services in Java and Spring Boot</li>
              <li>Own features from requirements through release and production support</li>
            </ul>
            <h3>Required Qualifications</h3>
            <ul>
              <li>5+ years of professional backend development experience</li>
              <li>Strong Java and Spring Boot</li>
              <li>Hands-on experience with AWS</li>
            </ul>
            <h3>Preferred Qualifications</h3>
            <ul>
              <li>Kafka or another event streaming platform</li>
            </ul>
            """;

    /** Board furniture that must not end up in the extracted posting. */
    private static final String CHROME = """
            <nav><a href="/">All jobs</a><a href="/teams">Teams</a></nav>
            <div id="other-jobs"><h4>Other openings</h4><ul>
              <li>Staff Data Engineer</li><li>Product Designer</li></ul></div>
            <form id="application_form"><label>Full name</label><input type="text">
              <label>Resume</label><input type="file"><button>Submit application</button></form>
            <footer>Acme Corp is an equal opportunity employer.</footer>
            """;

    public static String greenhouse() {
        return """
                <html><head><title>Senior Backend Engineer at Acme Corp</title></head><body>
                %s
                <div id="header">
                  <h1 class="app-title">Senior Backend Engineer</h1>
                  <span class="company-name">at Acme Corp</span>
                  <div class="location">Toronto, ON</div>
                </div>
                <div id="content">%s</div>
                %s
                </body></html>
                """.formatted(navOnly(), DESCRIPTION, CHROME);
    }

    public static String lever() {
        return """
                <html><head><title>Acme Corp - Senior Backend Engineer</title></head><body>
                %s
                <div class="posting-headline">
                  <h2>Senior Backend Engineer</h2>
                  <div class="posting-categories">
                    <span class="sort-by-time posting-category location">Toronto, ON</span>
                    <span class="sort-by-team posting-category department">Engineering</span>
                  </div>
                </div>
                <div data-qa="job-description" class="section-wrapper page-full-width">%s</div>
                %s
                </body></html>
                """.formatted(navOnly(), DESCRIPTION, CHROME);
    }

    /** Ashby ships hashed class names, so the selectors match on a substring. */
    public static String ashby() {
        return """
                <html><head><title>Senior Backend Engineer @ Acme</title></head><body>
                %s
                <div class="_jobPostingHeader_1a2b3">
                  <h1>Senior Backend Engineer</h1>
                  <div class="_location_9fe12">Toronto, ON</div>
                </div>
                <div class="_descriptionText_4c5d6">%s</div>
                %s
                </body></html>
                """.formatted(navOnly(), DESCRIPTION, CHROME);
    }

    public static String workday() {
        return """
                <html><head><title>Senior Backend Engineer</title></head><body>
                %s
                <h2 data-automation-id="jobPostingHeader">Senior Backend Engineer</h2>
                <div data-automation-id="locations"><dl><dd>Toronto, ON</dd></dl></div>
                <div data-automation-id="jobPostingDescription">%s</div>
                %s
                </body></html>
                """.formatted(navOnly(), DESCRIPTION, CHROME);
    }

    /** The shell a single-page board serves before its JavaScript runs. */
    public static String unrenderedShell() {
        return """
                <html><head><title>Acme Careers</title>
                <script src="/static/runtime.js"></script>
                <script src="/static/vendor.js"></script>
                <script src="/static/main.js"></script>
                </head><body><div id="root"></div></body></html>
                """;
    }

    private static String navOnly() {
        return "<nav><a href=\"/\">Acme</a></nav>";
    }
}
