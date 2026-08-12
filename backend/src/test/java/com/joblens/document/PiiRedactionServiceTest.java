package com.joblens.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PiiRedactionServiceTest {

    private final PiiRedactionService redaction = new PiiRedactionService();

    @ParameterizedTest
    @ValueSource(strings = {
            "alex.morgan@example.com", "a.m+jobs@sub.example.co.uk", "ALEX@EXAMPLE.COM"
    })
    void removesEmailAddresses(String email) {
        assertThat(redaction.redact("Contact: " + email))
                .doesNotContain(email)
                .contains("[EMAIL]");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+1 (416) 555-0142", "416-555-0142", "416.555.0142", "4165550142"
    })
    void removesPhoneNumbers(String phone) {
        assertThat(redaction.redact("Phone " + phone)).contains("[PHONE]").doesNotContain(phone);
    }

    @Test
    void removesStreetAddressesAndPostalCodes() {
        String redacted = redaction.redact("122 Wellington Street West, Apt 5, Toronto, ON M5V 3A8");

        assertThat(redacted).contains("[ADDRESS]", "[POSTAL CODE]");
        assertThat(redacted).doesNotContain("Wellington Street", "M5V 3A8");
    }

    @Test
    void removesTheCandidateName() {
        String redacted = redaction.redact("Alex Morgan\nAlex led the payments rewrite.", "Alex Morgan");

        assertThat(redacted).doesNotContain("Alex Morgan").doesNotContain("Alex led");
        assertThat(redacted).contains("[CANDIDATE]");
    }

    @Test
    void keepsTheCareerEvidenceThatTheAnalysisActuallyNeeds() {
        String resume = """
                Alex Morgan
                alex.morgan@example.com | 416-555-0142 | Toronto, ON M5V 3A8

                Senior Software Engineer, Northwind Systems   Mar 2021 - Present
                - Shipped a Spring Boot payments service handling 4,000 requests per minute.
                - Cut partner onboarding time by 40% with a React and TypeScript rebuild.
                """;

        String redacted = redaction.redact(resume, "Alex Morgan");

        assertThat(redacted)
                .as("employers, technologies, metrics and dates are the evidence being analysed")
                .contains("Northwind Systems", "Spring Boot", "4,000 requests per minute", "40%",
                        "Mar 2021 - Present", "React and TypeScript");
        assertThat(redacted).doesNotContain("alex.morgan@example.com", "416-555-0142");
    }

    @Test
    void leavesTextWithNoPersonalDetailsUntouched() {
        String text = "Built REST APIs in Java for a logistics platform used by 30 enterprise customers.";

        assertThat(redaction.redact(text)).isEqualTo(text);
    }
}
