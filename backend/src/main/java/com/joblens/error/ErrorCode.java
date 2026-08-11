package com.joblens.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * The stable error catalogue of the JobLens API.
 *
 * <p>Every failure the API can report is named here exactly once. Clients branch on {@link #name()},
 * never on the human-readable title or detail, so wording can be improved without breaking callers.
 * Codes for later phases are declared up front because the catalogue is part of the published API
 * contract; the handlers that raise them arrive with the feature that needs them.
 *
 * <p>Titles, details and recovery actions are user-facing product copy and are always English.
 * None of them may reveal internal hosts, addresses, file paths or stack traces.
 */
public enum ErrorCode {

    // --- Request shape and validation -------------------------------------------------------
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST,
            "Some fields need attention",
            "Correct the highlighted fields and submit again."),
    REQUEST_NOT_READABLE(HttpStatus.BAD_REQUEST,
            "The request could not be read",
            "Reload the page and try again."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported request format",
            "Reload the page and try again."),
    REQUEST_INVALID(HttpStatus.BAD_REQUEST,
            "The request could not be processed",
            "Reload the page and try again."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Not found",
            "Check the address, or start again from the upload step."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED,
            "Not supported at this address",
            "Reload the page and try again."),

    // --- Resume upload and extraction -------------------------------------------------------
    FILE_MISSING(HttpStatus.BAD_REQUEST,
            "No file was uploaded",
            "Choose a PDF resume and upload it again."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE,
            "This file is too large",
            "Upload a smaller PDF, or remove large images from your resume and export it again."),
    FILE_TYPE_NOT_SUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "This file is not a PDF",
            "Export your resume as a PDF and upload it again."),
    PDF_ENCRYPTED(HttpStatus.UNPROCESSABLE_CONTENT,
            "This PDF is password protected",
            "Remove the password from the PDF and upload it again."),
    PDF_CORRUPT(HttpStatus.UNPROCESSABLE_CONTENT,
            "This PDF could not be opened",
            "Export your resume to PDF again and upload the new file."),
    PDF_IMAGE_ONLY(HttpStatus.UNPROCESSABLE_CONTENT,
            "This PDF has no readable text",
            "This looks like a scanned or image-only PDF. JobLens does not support OCR yet, "
                    + "so upload a text-based PDF exported from your word processor."),
    PDF_TOO_MANY_PAGES(HttpStatus.UNPROCESSABLE_CONTENT,
            "This PDF has too many pages",
            "Upload a shorter resume."),
    RESUME_TEXT_TOO_SHORT(HttpStatus.UNPROCESSABLE_CONTENT,
            "Not enough resume text could be read",
            "Check that the PDF contains selectable text, or paste the content into a new PDF."),
    REVIEW_NOT_CONFIRMED(HttpStatus.BAD_REQUEST,
            "The reviewed content has not been confirmed",
            "Review the extracted resume and confirm it before running an analysis."),

    // --- Job description input --------------------------------------------------------------
    JD_INPUT_AMBIGUOUS(HttpStatus.BAD_REQUEST,
            "Provide either a job URL or pasted text",
            "Choose one input method and submit again."),
    JD_TEXT_TOO_SHORT(HttpStatus.BAD_REQUEST,
            "This job description is too short to analyse",
            "Paste the full job posting, including responsibilities and qualifications."),
    JD_TEXT_TOO_LONG(HttpStatus.BAD_REQUEST,
            "This job description is too long",
            "Paste only the job posting itself, without unrelated page content."),
    JD_URL_MODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "Reading a job URL is not available yet",
            "Open the posting, copy the job description, and use Paste Job Description instead."),
    JD_EXTRACTION_INSUFFICIENT(HttpStatus.UNPROCESSABLE_CONTENT,
            "Not enough job details could be read from that page",
            "Copy the job description from the page and use Paste Job Description instead."),

    // --- Job URL fetching ---------------------------------------------------------------------
    URL_INVALID(HttpStatus.BAD_REQUEST,
            "This job URL is not valid",
            "Check the address and paste the full link, including https://."),
    URL_SCHEME_NOT_ALLOWED(HttpStatus.BAD_REQUEST,
            "Only http and https links are supported",
            "Paste an https link to the public job posting."),
    URL_BLOCKED(HttpStatus.BAD_REQUEST,
            "This address cannot be fetched",
            "Paste a public job posting link, or use Paste Job Description instead."),
    URL_DISALLOWED_BY_ROBOTS(HttpStatus.UNPROCESSABLE_CONTENT,
            "This site does not allow automated access to that page",
            "Copy the job description from the page and use Paste Job Description instead."),
    URL_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT,
            "That page took too long to respond",
            "Try again, or use Paste Job Description instead."),
    URL_TOO_MANY_REDIRECTS(HttpStatus.UNPROCESSABLE_CONTENT,
            "That link redirected too many times",
            "Open the posting in your browser, copy the final link, and try again."),
    URL_RESPONSE_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT,
            "That page is too large to process",
            "Use Paste Job Description instead."),
    URL_CONTENT_TYPE_UNSUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT,
            "That link is not a job posting page",
            "Paste a link to the job posting page itself, or use Paste Job Description instead."),
    URL_LOGIN_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT,
            "That posting requires signing in",
            "Copy the job description after signing in, and use Paste Job Description instead."),
    URL_BLOCKED_BY_SITE(HttpStatus.UNPROCESSABLE_CONTENT,
            "That site blocked the request",
            "Copy the job description from the page and use Paste Job Description instead."),
    URL_FETCH_FAILED(HttpStatus.BAD_GATEWAY,
            "That page could not be loaded",
            "Try again, or use Paste Job Description instead."),

    // --- Analysis -------------------------------------------------------------------------------
    ANALYSIS_INPUT_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE,
            "There is too much content to analyse",
            "Shorten the resume or job description in the review step and run the analysis again."),
    AI_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "The analysis service is unavailable",
            "Wait a moment and run the analysis again."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT,
            "The analysis took too long",
            "Run the analysis again."),
    AI_OUTPUT_INVALID(HttpStatus.BAD_GATEWAY,
            "The analysis result could not be verified",
            "Run the analysis again. If this keeps happening, shorten the job description."),

    // --- Generic ----------------------------------------------------------------------------------
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS,
            "Too many requests",
            "Wait a moment before trying again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            "Something went wrong",
            "Try again. If the problem continues, start over from the upload step.");

    private static final String TYPE_PREFIX = "https://joblens.local/problems/";

    private final HttpStatus status;
    private final String title;
    private final String recoveryAction;

    ErrorCode(HttpStatus status, String title, String recoveryAction) {
        this.status = status;
        this.title = title;
        this.recoveryAction = recoveryAction;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    public String recoveryAction() {
        return recoveryAction;
    }

    /** Stable, dereferenceable-looking problem type identifier, e.g. {@code .../pdf-image-only}. */
    public String type() {
        return TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
