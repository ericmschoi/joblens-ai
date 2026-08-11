package com.joblens.api.jobposting;

import jakarta.validation.constraints.AssertTrue;

/**
 * A job description supplied as exactly one of a URL or pasted text.
 *
 * <p>Exactly one, not "at least one": if both arrive, there is no safe way to know which the user
 * meant, and picking one silently would analyse a posting they never chose.
 */
public record JobDescriptionExtractionRequest(String url, String text) {

    @AssertTrue(message = "Provide either a job URL or pasted text, not both.")
    public boolean isExactlyOneSourceProvided() {
        return hasUrl() ^ hasText();
    }

    public boolean hasUrl() {
        return url != null && !url.isBlank();
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}
