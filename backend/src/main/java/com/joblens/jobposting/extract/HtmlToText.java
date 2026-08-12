package com.joblens.jobposting.extract;

import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * Turns job-posting HTML into the plain text the parser reads.
 *
 * <p>List structure is preserved on purpose. In a posting, whether two requirements are one
 * {@code <li>} or two decides how many requirements exist, and flattening the markup would silently
 * merge or split them.
 *
 * <p>Nothing here produces HTML. Fetched markup is never handed to the browser; only text is, so
 * there is no path from a hostile page to script execution in the review screen.
 */
final class HtmlToText {

    private static final List<String> NOISE_SELECTORS = List.of(
            "script", "style", "noscript", "iframe", "svg", "template", "nav", "header", "footer",
            "aside", "form", "[aria-hidden=true]");

    /** Containers a posting's body is usually in, most specific first. */
    private static final List<String> CONTENT_SELECTORS = List.of(
            "[data-testid*=job-description]", "[class*=job-description]", "[id*=job-description]",
            "[class*=jobDescription]", "[id*=jobDescription]", "[class*=job-details]",
            "article", "main", "[role=main]", "#content", "body");

    private HtmlToText() {}

    static String fromDocument(String html) {
        Document document = Jsoup.parse(html);
        NOISE_SELECTORS.forEach(selector -> document.select(selector).remove());

        Element content = CONTENT_SELECTORS.stream()
                .map(selector -> document.selectFirst(selector))
                .filter(element -> element != null && element.text().length() > 200)
                .findFirst()
                .orElse(document.body());

        return content == null ? "" : toText(content);
    }

    /** For an HTML fragment such as a JSON-LD {@code description}, which has no page chrome. */
    static String fromFragment(String html) {
        return toText(Jsoup.parseBodyFragment(Jsoup.clean(html, Safelist.relaxed())).body());
    }

    /** Reads the first selector that matches, for boards whose layout is known. */
    static java.util.Optional<String> fromSelectors(Document document, List<String> selectors) {
        NOISE_SELECTORS.forEach(selector -> document.select(selector).remove());
        return selectors.stream()
                .map(document::selectFirst)
                .filter(java.util.Objects::nonNull)
                .map(HtmlToText::toText)
                .filter(text -> !text.isBlank())
                .findFirst();
    }

    /** A short field such as a title or a location, where only the plain text matters. */
    static String firstMatchingText(Document document, List<String> selectors) {
        return selectors.stream()
                .map(document::selectFirst)
                .filter(java.util.Objects::nonNull)
                .map(element -> element.text().strip())
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String toText(Element root) {
        StringBuilder text = new StringBuilder();
        appendChildren(root, text);
        return text.toString()
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private static void appendChildren(Element element, StringBuilder text) {
        for (org.jsoup.nodes.Node node : element.childNodes()) {
            if (node instanceof org.jsoup.nodes.TextNode textNode) {
                text.append(textNode.text());
            } else if (node instanceof Element child) {
                appendElement(child, text);
            }
        }
    }

    private static void appendElement(Element element, StringBuilder text) {
        String tag = element.tagName();

        if (tag.equals("br")) {
            text.append('\n');
            return;
        }
        if (tag.equals("li")) {
            newLine(text);
            text.append("- ");
            appendChildren(element, text);
            text.append('\n');
            return;
        }
        if (element.isBlock()) {
            newLine(text);
            appendChildren(element, text);
            text.append('\n');
            return;
        }
        appendChildren(element, text);
    }

    private static void newLine(StringBuilder text) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
    }
}
