package com.joblens.jobposting.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads a job posting out of a fetched page, preferring the site's own structured data.
 *
 * <p>Many job boards publish schema.org {@code JobPosting} data so search engines can index them
 * accurately. When it is there it is far better than anything guessed from markup, because the site
 * has already said which text is the title, the employer and the description. Ordinary HTML reading
 * is the fallback.
 */
@Component
public class PageContentExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(PageContentExtractor.class);
    private static final String JOB_POSTING_TYPE = "jobposting";

    private final ObjectMapper objectMapper;

    public PageContentExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ExtractedPageContent extract(String html) {
        return fromJsonLd(html).orElseGet(() -> ExtractedPageContent.generic(HtmlToText.fromDocument(html)));
    }

    private Optional<ExtractedPageContent> fromJsonLd(String html) {
        for (Element script : Jsoup.parse(html).select("script[type=application/ld+json]")) {
            Optional<JsonNode> posting = readPosting(script.data());
            if (posting.isPresent()) {
                return posting.map(this::toContent).filter(content -> !content.text().isBlank());
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> readPosting(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return findJobPosting(objectMapper.readTree(json));
        } catch (RuntimeException e) {
            // Malformed structured data is common and is simply not usable. The HTML fallback runs.
            LOG.debug("ignoring unreadable ld+json block: cause={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Structured data arrives as a single object, an array, or an object wrapping an {@code @graph}. */
    private static Optional<JsonNode> findJobPosting(JsonNode node) {
        if (node.isArray()) {
            for (JsonNode element : node) {
                Optional<JsonNode> found = findJobPosting(element);
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        }
        if (!node.isObject()) {
            return Optional.empty();
        }
        if (node.has("@graph")) {
            return findJobPosting(node.get("@graph"));
        }
        return isJobPosting(node.get("@type")) ? Optional.of(node) : Optional.empty();
    }

    private static boolean isJobPosting(JsonNode type) {
        if (type == null) {
            return false;
        }
        if (type.isArray()) {
            for (JsonNode element : type) {
                if (isJobPosting(element)) {
                    return true;
                }
            }
            return false;
        }
        return type.isString() && type.stringValue().toLowerCase(Locale.ROOT).endsWith(JOB_POSTING_TYPE);
    }

    private ExtractedPageContent toContent(JsonNode posting) {
        String description = text(posting, "description");
        return new ExtractedPageContent(
                ExtractedPageContent.Strategy.JSON_LD,
                description == null ? "" : HtmlToText.fromFragment(description),
                text(posting, "title"),
                nested(posting, "hiringOrganization", "name"),
                location(posting),
                employmentType(posting),
                compensation(posting));
    }

    private static String location(JsonNode posting) {
        JsonNode jobLocation = posting.get("jobLocation");
        JsonNode first = jobLocation != null && jobLocation.isArray() && !jobLocation.isEmpty()
                ? jobLocation.get(0)
                : jobLocation;
        if (first == null) {
            return remoteFlag(posting);
        }

        JsonNode address = first.get("address");
        if (address == null) {
            return remoteFlag(posting);
        }
        List<String> parts = new ArrayList<>();
        for (String field : List.of("addressLocality", "addressRegion", "addressCountry")) {
            String value = text(address, field);
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }
        String joined = String.join(", ", parts);
        String remote = remoteFlag(posting);
        if (remote != null) {
            return joined.isEmpty() ? remote : joined + " (" + remote + ")";
        }
        return joined.isEmpty() ? null : joined;
    }

    private static String remoteFlag(JsonNode posting) {
        String type = text(posting, "jobLocationType");
        return type != null && type.toLowerCase(Locale.ROOT).contains("telecommute") ? "Remote" : null;
    }

    private static String employmentType(JsonNode posting) {
        JsonNode value = posting.get("employmentType");
        if (value == null) {
            return null;
        }
        if (value.isArray()) {
            List<String> types = new ArrayList<>();
            value.forEach(element -> types.add(readable(element.asString())));
            return types.isEmpty() ? null : String.join(", ", types);
        }
        return readable(value.asString());
    }

    /** Structured data writes {@code FULL_TIME}; people read "Full time". */
    private static String readable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String spaced = value.replace('_', ' ').toLowerCase(Locale.ROOT).strip();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String compensation(JsonNode posting) {
        JsonNode salary = posting.get("baseSalary");
        if (salary == null) {
            return null;
        }
        JsonNode value = salary.get("value");
        if (value == null) {
            return text(salary, "name");
        }
        String currency = text(salary, "currency");
        String min = text(value, "minValue");
        String max = text(value, "maxValue");
        String single = text(value, "value");
        String unit = text(value, "unitText");

        String amount;
        if (min != null && max != null) {
            amount = min + " - " + max;
        } else if (single != null) {
            amount = single;
        } else {
            return null;
        }
        return ((currency == null ? "" : currency + " ") + amount
                + (unit == null ? "" : " per " + unit.toLowerCase(Locale.ROOT))).strip();
    }

    private static String nested(JsonNode node, String parent, String field) {
        JsonNode child = node.get(parent);
        return child == null ? null : text(child, field);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String asText = value.isString() ? value.stringValue() : value.asString();
        return asText == null || asText.isBlank() ? null : asText.strip();
    }
}
