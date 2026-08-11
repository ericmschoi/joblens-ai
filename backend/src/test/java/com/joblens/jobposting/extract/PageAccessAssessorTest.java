package com.joblens.jobposting.extract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PageAccessAssessorTest {

    private static final int MIN_CHARACTERS = 200;

    private final PageAccessAssessor assessor = new PageAccessAssessor();

    private PageAccess assess(String html, String extractedText) {
        return assessor.assess(html, extractedText, MIN_CHARACTERS);
    }

    private static String posting() {
        return "Senior Backend Engineer at Acme Corp. We are looking for an engineer to design and "
                + "build backend services in Java, own features through to production, and help keep "
                + "the platform reliable for the businesses that depend on it every day.";
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Just a moment...",
            "Attention Required! | Cloudflare",
            "Access denied",
            "Are you a robot?",
            "Security check",
            "Pardon Our Interruption"
    })
    void recognisesABotCheckFromItsTitleAlone(String title) {
        String html = "<html><head><title>" + title + "</title></head><body><p>Checking.</p></body></html>";

        assertThat(assess(html, "Checking.")).isEqualTo(PageAccess.BOT_CHECK);
    }

    @Test
    void recognisesABotCheckFromItsBodyWhenThereIsNoPosting() {
        String html = "<html><head><title>acme.com</title></head><body>"
                + "<p>Please enable JavaScript and cookies to continue. Ray ID: 8c1f2a</p></body></html>";

        assertThat(assess(html, "Please enable JavaScript and cookies to continue. Ray ID: 8c1f2a"))
                .isEqualTo(PageAccess.BOT_CHECK);
    }

    @Test
    void recognisesASignInWall() {
        String html = "<html><head><title>Sign in | Acme Jobs</title></head><body>"
                + "<form><input type=\"email\"><input type=\"password\"></form></body></html>";

        assertThat(assess(html, "Sign in")).isEqualTo(PageAccess.LOGIN_WALL);
    }

    @Test
    void recognisesASignInWallFromItsWording() {
        String html = "<html><head><title>Acme Jobs</title></head><body>"
                + "<p>Please sign in to view this job posting.</p></body></html>";

        assertThat(assess(html, "Please sign in to view this job posting."))
                .isEqualTo(PageAccess.LOGIN_WALL);
    }

    @Test
    void recognisesAPageThatBuildsItselfInTheBrowser() {
        String html = "<html><head><title>Acme Careers</title>"
                + "<script src=\"/a.js\"></script><script src=\"/b.js\"></script>"
                + "<script src=\"/c.js\"></script></head>"
                + "<body><div id=\"root\"></div></body></html>";

        assertThat(assess(html, "")).isEqualTo(PageAccess.JAVASCRIPT_REQUIRED);
    }

    @Test
    void treatsAnOrdinaryPostingAsReadable() {
        String html = "<html><head><title>Senior Backend Engineer at Acme</title></head><body><main>"
                + posting() + "</main></body></html>";

        assertThat(assess(html, posting())).isEqualTo(PageAccess.READABLE);
    }

    @Test
    void doesNotMistakeAPostingThatMentionsCaptchasForABotCheck() {
        String text = posting() + " You will work on CAPTCHA and bot verification defences, and will "
                + "help design the sign in experience for our customers.";
        String html = "<html><head><title>Security Engineer at Acme</title></head><body><main>"
                + text + "</main></body></html>";

        assertThat(assess(html, text))
                .as("a page with a posting's worth of text is a posting, whatever words are in it")
                .isEqualTo(PageAccess.READABLE);
    }

    @Test
    void doesNotMistakeAPostingWithALoginLinkInItsChromeForALoginWall() {
        String html = "<html><head><title>Senior Backend Engineer at Acme</title></head><body>"
                + "<nav><a href=\"/login\">Log in</a></nav><main>" + posting() + "</main></body></html>";

        assertThat(assess(html, posting())).isEqualTo(PageAccess.READABLE);
    }

    @Test
    void aShortPageThatIsNeitherAWallNorAnAppShellIsSimplyReadable() {
        String html = "<html><head><title>Acme</title></head><body><p>Nothing here yet.</p></body></html>";

        assertThat(assess(html, "Nothing here yet."))
                .as("it is not a refusal, so the caller reports insufficient content instead")
                .isEqualTo(PageAccess.READABLE);
    }
}
