package com.joblens.jobposting.extract;

/**
 * What a page turned out to be, when it did not turn out to be a readable job posting.
 *
 * <p>The distinction matters because the responses differ. A page that refused automated access is
 * a decision by its owner and JobLens accepts it. A page that simply builds its content with
 * JavaScript is a technical limitation JobLens may eventually work around.
 */
public enum PageAccess {

    /** Enough text was read to attempt an extraction. */
    READABLE,

    /** A bot check, CAPTCHA or interstitial challenge stood in front of the content. */
    BOT_CHECK,

    /** The content is behind a sign-in. */
    LOGIN_WALL,

    /**
     * A shell page whose content is assembled in the browser. The only case a rendering fallback
     * would legitimately help with.
     */
    JAVASCRIPT_REQUIRED
}
