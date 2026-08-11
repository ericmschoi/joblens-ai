package com.joblens.resume;

import com.joblens.config.JoblensProperties;
import com.joblens.document.ExtractionWarning;
import com.joblens.document.InstructionLikeText;
import com.joblens.document.WarningCode;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

/**
 * Reads text out of a PDF and reports how much it should be trusted.
 *
 * <p>Extraction is imperfect by nature: two-column layouts, repeated headers and decorative letter
 * spacing all corrupt the result in ways a parser cannot fix reliably. Rather than hide that, every
 * detectable problem becomes a warning the user sees before the analysis runs, which is why the
 * review step exists at all.
 *
 * <p>Everything happens in memory. No temporary file is written, so there is nothing to clean up
 * and nothing left behind on disk.
 */
@Service
public class PdfTextExtractionService {

    /** Below this, a page with images is almost certainly a scan rather than a text page. */
    private static final int SCANNED_PAGE_CHAR_THRESHOLD = 60;

    /** Reading-order agreement below this suggests the text is laid out in columns. */
    private static final double READING_ORDER_AGREEMENT_THRESHOLD = 0.9;

    private static final double BROKEN_WORD_RATIO_THRESHOLD = 0.15;
    private static final int BROKEN_WORD_MIN_TOKENS = 50;
    private static final int REPEATED_LINE_MIN_PAGES = 3;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final JoblensProperties.Resume limits;

    public PdfTextExtractionService(JoblensProperties properties) {
        this.limits = properties.resume();
    }

    public ExtractedResumeText extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            return extractFrom(document);
        } catch (InvalidPasswordException e) {
            throw new ApiException(ErrorCode.PDF_ENCRYPTED,
                    "This PDF is protected with a password, so its text cannot be read.", e);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            // The parser's own message can contain fragments of the file and is never surfaced.
            throw new ApiException(ErrorCode.PDF_CORRUPT, "This PDF could not be opened.", e);
        }
    }

    private ExtractedResumeText extractFrom(PDDocument document) throws IOException {
        int pageCount = document.getNumberOfPages();
        if (pageCount > limits.maxPageCount()) {
            throw new ApiException(ErrorCode.PDF_TOO_MANY_PAGES,
                    "This PDF has %d pages. The limit is %d.".formatted(pageCount, limits.maxPageCount()));
        }

        List<ExtractionWarning> warnings = new ArrayList<>();
        if (document.isEncrypted()) {
            warnings.add(ExtractionWarning.of(WarningCode.ENCRYPTED_BUT_READABLE));
        }

        List<String> pageTexts = new ArrayList<>(pageCount);
        List<ExtractedResumeText.PageInfo> pages = new ArrayList<>(pageCount);

        StringBuilder combined = new StringBuilder();
        boolean truncated = false;

        for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
            String pageText = readPage(document, pageNumber);
            pageTexts.add(pageText);

            boolean hasImages = pageDrawsImages(document.getPage(pageNumber - 1));
            pages.add(new ExtractedResumeText.PageInfo(pageNumber, pageText.strip().length(), hasImages));

            if (!truncated) {
                int remaining = limits.maxExtractedCharacters() - combined.length();
                if (pageText.length() > remaining) {
                    combined.append(pageText, 0, Math.max(remaining, 0));
                    truncated = true;
                } else {
                    combined.append(pageText);
                }
            }
        }

        String rawText = combined.toString().strip();
        if (truncated) {
            warnings.add(ExtractionWarning.of(WarningCode.TEXT_TRUNCATED));
        }

        rejectUnusableDocument(rawText, pages);
        warnings.addAll(qualityWarnings(document, rawText, pageTexts, pages));

        return new ExtractedResumeText(rawText, pages, warnings);
    }

    /** A document JobLens cannot honestly analyse must fail loudly rather than return an empty result. */
    private void rejectUnusableDocument(String rawText, List<ExtractedResumeText.PageInfo> pages) {
        if (rawText.length() >= limits.minUsableCharacters()) {
            return;
        }

        boolean looksScanned = pages.stream()
                .anyMatch(page -> page.hasImages() && page.charCount() < SCANNED_PAGE_CHAR_THRESHOLD);

        if (looksScanned) {
            throw new ApiException(ErrorCode.PDF_IMAGE_ONLY,
                    "This PDF appears to contain images of text rather than text itself.");
        }
        throw new ApiException(ErrorCode.RESUME_TEXT_TOO_SHORT,
                "Only %d characters of text could be read from this PDF.".formatted(rawText.length()));
    }

    private List<ExtractionWarning> qualityWarnings(PDDocument document, String rawText,
            List<String> pageTexts, List<ExtractedResumeText.PageInfo> pages) throws IOException {

        List<ExtractionWarning> warnings = new ArrayList<>();

        if (readingOrderIsAmbiguous(document, rawText)) {
            warnings.add(ExtractionWarning.of(WarningCode.POSSIBLE_MULTI_COLUMN));
        }
        if (hasRepeatedEdgeLines(pageTexts)) {
            warnings.add(ExtractionWarning.of(WarningCode.REPEATED_HEADER_FOOTER));
        }
        if (looksLikeBrokenWords(rawText)) {
            warnings.add(ExtractionWarning.of(WarningCode.BROKEN_WORDS));
        }
        if (InstructionLikeText.isPresentIn(rawText)) {
            warnings.add(ExtractionWarning.of(WarningCode.POSSIBLE_EMBEDDED_INSTRUCTIONS));
        }
        for (ExtractedResumeText.PageInfo page : pages) {
            if (page.hasImages() && page.charCount() < SCANNED_PAGE_CHAR_THRESHOLD) {
                warnings.add(ExtractionWarning.onPage(WarningCode.LOW_TEXT_DENSITY, page.pageNumber()));
            }
        }
        return warnings;
    }

    private static String readPage(PDDocument document, int pageNumber) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(document);
    }

    /**
     * Compares position-sorted text with the order the drawing operations appear in. A single-column
     * document reads the same either way; a columned layout does not, and that disagreement is the
     * most reliable signal available without a full layout engine.
     */
    private static boolean readingOrderIsAmbiguous(PDDocument document, String sortedText) throws IOException {
        PDFTextStripper contentOrder = new PDFTextStripper();
        contentOrder.setSortByPosition(false);
        String unsorted = contentOrder.getText(document);

        List<String> a = tokenize(sortedText);
        List<String> b = tokenize(unsorted);
        if (a.size() < 20 || b.isEmpty()) {
            return false;
        }

        int comparable = Math.min(a.size(), b.size());
        int matches = 0;
        for (int i = 0; i < comparable; i++) {
            if (a.get(i).equals(b.get(i))) {
                matches++;
            }
        }
        double agreement = (double) matches / Math.max(a.size(), b.size());
        return agreement < READING_ORDER_AGREEMENT_THRESHOLD;
    }

    private static boolean hasRepeatedEdgeLines(List<String> pageTexts) {
        if (pageTexts.size() < REPEATED_LINE_MIN_PAGES) {
            return false;
        }
        return isRepeated(edgeLines(pageTexts, true)) || isRepeated(edgeLines(pageTexts, false));
    }

    private static List<String> edgeLines(List<String> pageTexts, boolean first) {
        List<String> edges = new ArrayList<>();
        for (String pageText : pageTexts) {
            List<String> lines = pageText.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
            if (!lines.isEmpty()) {
                edges.add(first ? lines.getFirst() : lines.getLast());
            }
        }
        return edges;
    }

    private static boolean isRepeated(List<String> lines) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String line : lines) {
            counts.merge(line, 1, Integer::sum);
        }
        return counts.values().stream().anyMatch(count -> count >= REPEATED_LINE_MIN_PAGES);
    }

    /**
     * Some PDFs place each glyph separately, which extracts as {@code "E R I C  C H O I"}. A high
     * proportion of single-character tokens is the signature of that.
     */
    private static boolean looksLikeBrokenWords(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.size() < BROKEN_WORD_MIN_TOKENS) {
            return false;
        }
        long singles = tokens.stream()
                .filter(token -> token.length() == 1)
                .filter(token -> Character.isLetter(token.charAt(0)))
                .filter(token -> !token.equals("a") && !token.equals("i"))
                .count();
        return (double) singles / tokens.size() > BROKEN_WORD_RATIO_THRESHOLD;
    }

    private static boolean pageDrawsImages(PDPage page) {
        PDResources resources = page.getResources();
        if (resources == null) {
            return false;
        }
        for (COSName name : resources.getXObjectNames()) {
            if (resources.isImageXObject(name)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> tokenize(String text) {
        return Arrays.stream(WHITESPACE.split(text.toLowerCase(Locale.ROOT).strip()))
                .filter(token -> !token.isEmpty())
                .toList();
    }
}
