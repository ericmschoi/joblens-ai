package com.joblens.resume;

import com.joblens.document.ExtractionWarning;
import com.joblens.document.Provenance;
import com.joblens.document.WarningCode;
import com.joblens.resume.model.CandidateProfile;
import com.joblens.resume.model.Certification;
import com.joblens.resume.model.DateRange;
import com.joblens.resume.model.Education;
import com.joblens.resume.model.Project;
import com.joblens.resume.model.SkillMention;
import com.joblens.resume.model.WorkExperience;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Turns resume text into a structured profile.
 *
 * <p>This is normalization, not interpretation. Nothing is inferred that the document does not
 * state, and when the structure cannot be recognised the raw text still stands on its own and a
 * warning tells the user the structured view is incomplete.
 *
 * <p>It runs on plain text rather than on the PDF, because the authoritative input is the version
 * the user confirms in the review step. That is also why {@link Provenance} here records sections
 * and line ranges rather than page numbers: once the user edits the text, page numbers no longer
 * describe anything real.
 */
@Service
public class ResumeNormalizer {

    private static final Map<Section, List<String>> SECTION_ALIASES = Map.of(
            Section.SUMMARY, List.of("summary", "professional summary", "profile", "objective",
                    "about", "about me", "overview"),
            Section.SKILLS, List.of("skills", "technical skills", "technologies", "core competencies",
                    "technical proficiencies", "tools", "tech stack"),
            Section.EXPERIENCE, List.of("experience", "work experience", "professional experience",
                    "employment", "employment history", "work history", "career history"),
            Section.EDUCATION, List.of("education", "academic background", "academics"),
            Section.PROJECTS, List.of("projects", "selected projects", "personal projects",
                    "side projects", "notable projects"),
            Section.CERTIFICATIONS, List.of("certifications", "certificates", "licenses", "licences",
                    "credentials"));

    private static final Pattern BULLET = Pattern.compile("^\\s*[\\u2022\\u25CF\\u25AA\\u2023\\u00B7*\\-\\u2013]\\s+");
    private static final Pattern STRONG_SEPARATOR = Pattern.compile("\\s*(?:\\u2014|\\u2013|\\||\\u00B7|\\t|\\s{3,})\\s*");
    private static final Pattern SKILL_SEPARATOR = Pattern.compile("[,;|\\u2022\\u00B7/]");
    private static final int MAX_HEADING_LENGTH = 40;

    private static final List<String> CREDENTIAL_KEYWORDS = List.of(
            "bachelor", "master", "b.sc", "bsc", "b.a", "b.eng", "beng", "m.sc", "msc", "mba",
            "ph.d", "phd", "doctorate", "diploma", "certificate", "associate", "degree");

    private enum Section {
        NONE, SUMMARY, SKILLS, EXPERIENCE, EDUCATION, PROJECTS, CERTIFICATIONS
    }

    public NormalizedResume normalize(String rawText) {
        List<String> lines = rawText.lines().map(line -> line.replace(' ', ' ')).toList();
        Map<Section, List<Line>> sections = splitIntoSections(lines);

        List<ExtractionWarning> warnings = new ArrayList<>();
        if (sections.keySet().stream().allMatch(section -> section == Section.NONE)) {
            warnings.add(ExtractionWarning.of(WarningCode.NO_SECTIONS_DETECTED));
        }

        List<WorkExperience> experiences = readExperiences(sections.getOrDefault(Section.EXPERIENCE, List.of()));
        List<Project> projects = readProjects(sections.getOrDefault(Section.PROJECTS, List.of()));

        List<SkillMention> skills = new ArrayList<>(
                readListedSkills(sections.getOrDefault(Section.SKILLS, List.of())));
        skills.addAll(findDemonstratedSkills(skills, experiences, projects));

        CandidateProfile profile = new CandidateProfile(
                readSummary(sections.getOrDefault(Section.SUMMARY, List.of())),
                skills,
                experiences,
                readEducation(sections.getOrDefault(Section.EDUCATION, List.of())),
                projects,
                readCertifications(sections.getOrDefault(Section.CERTIFICATIONS, List.of())));

        return new NormalizedResume(profile, warnings);
    }

    // --- sectioning ------------------------------------------------------------------------------

    private record Line(int index, String text) {

        boolean isBlank() {
            return text.isBlank();
        }

        boolean isBullet() {
            return BULLET.matcher(text).find();
        }

        String stripped() {
            return BULLET.matcher(text).replaceFirst("").strip();
        }
    }

    private static Map<Section, List<Line>> splitIntoSections(List<String> lines) {
        Map<Section, List<Line>> sections = new LinkedHashMap<>();
        Section current = Section.NONE;

        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            Optional<Section> heading = headingFor(text);
            if (heading.isPresent()) {
                current = heading.get();
                sections.computeIfAbsent(current, key -> new ArrayList<>());
                continue;
            }
            if (!text.isBlank()) {
                sections.computeIfAbsent(current, key -> new ArrayList<>()).add(new Line(i, text));
            }
        }
        return sections;
    }

    private static Optional<Section> headingFor(String line) {
        String candidate = line.strip().replaceAll("[:\\s]+$", "").toLowerCase(Locale.ROOT);
        if (candidate.isEmpty() || candidate.length() > MAX_HEADING_LENGTH || candidate.endsWith(".")) {
            return Optional.empty();
        }
        for (Map.Entry<Section, List<String>> entry : SECTION_ALIASES.entrySet()) {
            if (entry.getValue().contains(candidate)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    // --- section readers -------------------------------------------------------------------------

    private static String readSummary(List<Line> lines) {
        return lines.stream().map(Line::stripped).reduce((a, b) -> a + " " + b).orElse("").strip();
    }

    private static List<SkillMention> readListedSkills(List<Line> lines) {
        Map<String, SkillMention> unique = new LinkedHashMap<>();
        for (Line line : lines) {
            String text = line.stripped();
            int colon = text.indexOf(':');
            if (colon >= 0 && colon < text.length() - 1) {
                text = text.substring(colon + 1);
            }
            for (String part : SKILL_SEPARATOR.split(text)) {
                String name = part.strip();
                if (name.length() < 2 || name.length() > 40) {
                    continue;
                }
                unique.putIfAbsent(name.toLowerCase(Locale.ROOT), new SkillMention(
                        "skill-" + (unique.size() + 1),
                        name,
                        SkillMention.Origin.SKILLS_LIST,
                        Provenance.of(null, "SKILLS", line.index(), line.index(), line.text().strip())));
            }
        }
        return List.copyOf(unique.values());
    }

    private static List<WorkExperience> readExperiences(List<Line> lines) {
        List<Integer> anchors = anchorIndexes(lines);
        List<WorkExperience> experiences = new ArrayList<>();

        for (int a = 0; a < anchors.size(); a++) {
            int anchor = anchors.get(a);
            int end = a + 1 < anchors.size() ? anchors.get(a + 1) : lines.size();
            Line headerLine = lines.get(anchor);

            String previousLine = precedingContextLine(lines, anchor, anchors);
            int consumedEnd = end;
            if (a + 1 < anchors.size() && precedingContextLine(lines, anchors.get(a + 1), anchors) != null) {
                consumedEnd = end - 1;
            }

            Header header = parseHeader(headerLine.stripped(), previousLine);
            List<String> bullets = new ArrayList<>();
            for (int i = anchor + 1; i < consumedEnd; i++) {
                bullets.add(lines.get(i).stripped());
            }

            experiences.add(new WorkExperience(
                    "exp-" + (experiences.size() + 1),
                    header.company(),
                    header.title(),
                    header.location(),
                    header.dates(),
                    bullets,
                    Provenance.of(null, "EXPERIENCE", headerLine.index(), lastIndex(lines, consumedEnd, headerLine),
                            headerLine.text().strip())));
        }
        return experiences;
    }

    private static List<Education> readEducation(List<Line> lines) {
        List<Education> education = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            String text = line.stripped();
            if (!mentionsCredential(text)) {
                continue;
            }
            Optional<DateRange> dates = DateRangeParser.findIn(text);
            String withoutDate = removeDate(text);
            List<String> chunks = splitStrong(withoutDate);

            // The institution sits on the same line, the line below, or the line above, depending on
            // the template. Prefer the same line, then the one below, which is the common layout.
            String credential = chunks.isEmpty() ? withoutDate : chunks.getFirst();
            String institution = chunks.size() > 1 ? chunks.get(1) : adjacentInstitution(lines, i);

            education.add(new Education(
                    "edu-" + (education.size() + 1),
                    institution,
                    credential,
                    dates.orElseGet(() -> DateRange.unparsed("")),
                    Provenance.of(null, "EDUCATION", line.index(), line.index(), line.text().strip())));
        }
        return education;
    }

    private static List<Project> readProjects(List<Line> lines) {
        List<Project> projects = new ArrayList<>();
        String currentName = null;
        int currentIndex = -1;
        List<String> bullets = new ArrayList<>();

        for (Line line : lines) {
            if (line.isBullet() && currentName != null) {
                bullets.add(line.stripped());
                continue;
            }
            if (currentName != null) {
                projects.add(newProject(projects.size(), currentName, bullets, currentIndex, line.index() - 1));
                bullets = new ArrayList<>();
            }
            currentName = removeDate(line.stripped());
            currentIndex = line.index();
        }
        if (currentName != null) {
            projects.add(newProject(projects.size(), currentName, bullets, currentIndex, currentIndex));
        }
        return projects;
    }

    private static Project newProject(int existing, String name, List<String> bullets, int start, int end) {
        return new Project("proj-" + (existing + 1), name, bullets,
                Provenance.of(null, "PROJECTS", start, Math.max(end, start), name));
    }

    private static List<Certification> readCertifications(List<Line> lines) {
        List<Certification> certifications = new ArrayList<>();
        for (Line line : lines) {
            String name = line.stripped();
            if (name.isBlank()) {
                continue;
            }
            certifications.add(new Certification(
                    "cert-" + (certifications.size() + 1),
                    name,
                    Provenance.of(null, "CERTIFICATIONS", line.index(), line.index(), line.text().strip())));
        }
        return certifications;
    }

    // --- skill cross-referencing -----------------------------------------------------------------

    /**
     * Records where a listed skill is actually demonstrated.
     *
     * <p>This is what lets scoring distinguish a technology that only appears in a skills list from
     * one described inside real work, without asking a model to make that judgement.
     */
    private static List<SkillMention> findDemonstratedSkills(List<SkillMention> listed,
            List<WorkExperience> experiences, List<Project> projects) {

        List<SkillMention> demonstrated = new ArrayList<>();
        int sequence = listed.size();

        for (SkillMention skill : listed) {
            String term = skill.name().toLowerCase(Locale.ROOT);

            for (WorkExperience experience : experiences) {
                Optional<String> bullet = firstMentioning(experience.bullets(), term);
                if (bullet.isPresent()) {
                    demonstrated.add(new SkillMention("skill-" + (++sequence), skill.name(),
                            SkillMention.Origin.WORK_EXPERIENCE,
                            Provenance.of(null, "EXPERIENCE", experience.provenance().lineStart(),
                                    experience.provenance().lineEnd(), bullet.get())));
                }
            }
            for (Project project : projects) {
                Optional<String> bullet = firstMentioning(project.bullets(), term);
                if (bullet.isPresent()) {
                    demonstrated.add(new SkillMention("skill-" + (++sequence), skill.name(),
                            SkillMention.Origin.PROJECT,
                            Provenance.of(null, "PROJECTS", project.provenance().lineStart(),
                                    project.provenance().lineEnd(), bullet.get())));
                }
            }
        }
        return demonstrated;
    }

    private static Optional<String> firstMentioning(List<String> bullets, String term) {
        return bullets.stream().filter(bullet -> containsTerm(bullet.toLowerCase(Locale.ROOT), term)).findFirst();
    }

    /** Whole-term containment that tolerates names regular expressions handle badly, such as C++ or .NET. */
    private static boolean containsTerm(String haystack, String term) {
        int from = 0;
        while (true) {
            int index = haystack.indexOf(term, from);
            if (index < 0) {
                return false;
            }
            int end = index + term.length();
            boolean leftClear = index == 0 || !Character.isLetterOrDigit(haystack.charAt(index - 1));
            boolean rightClear = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (leftClear && rightClear) {
                return true;
            }
            from = end;
        }
    }

    // --- header parsing --------------------------------------------------------------------------

    private record Header(String title, String company, String location, DateRange dates) {}

    private static Header parseHeader(String headerText, String previousLine) {
        DateRange dates = DateRangeParser.findIn(headerText).orElseGet(() -> DateRange.unparsed(""));
        List<String> chunks = splitStrong(removeDate(headerText));

        String title = chunks.isEmpty() ? headerText : chunks.getFirst();
        String company = chunks.size() > 1 ? chunks.get(1) : null;
        String location = chunks.size() > 2 ? chunks.get(2) : null;

        // "Senior Engineer, Acme Corp" packs two fields into one chunk.
        int lastComma = title.lastIndexOf(", ");
        if (lastComma > 0 && company == null) {
            company = title.substring(lastComma + 2).strip();
            title = title.substring(0, lastComma).strip();
        } else if (lastComma > 0) {
            location = company;
            company = title.substring(lastComma + 2).strip();
            title = title.substring(0, lastComma).strip();
        }

        if (company == null && previousLine != null) {
            company = previousLine;
        }
        return new Header(title, company, location, dates);
    }

    private static List<Integer> anchorIndexes(List<Line> lines) {
        List<Integer> anchors = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            if (!line.isBullet() && DateRangeParser.findRawIn(line.text()).isPresent()) {
                anchors.add(i);
            }
        }
        return anchors;
    }

    /** A company or title written on its own line directly above the line carrying the dates. */
    private static String precedingContextLine(List<Line> lines, int anchor, List<Integer> anchors) {
        int previous = anchor - 1;
        if (previous < 0 || anchors.contains(previous)) {
            return null;
        }
        Line line = lines.get(previous);
        if (line.isBullet() || line.isBlank()) {
            return null;
        }
        return line.stripped();
    }

    private static String adjacentInstitution(List<Line> lines, int index) {
        String below = neighbour(lines, index + 1);
        if (below != null) {
            return below;
        }
        return neighbour(lines, index - 1);
    }

    private static String neighbour(List<Line> lines, int index) {
        if (index < 0 || index >= lines.size()) {
            return null;
        }
        Line line = lines.get(index);
        if (line.isBlank() || mentionsCredential(line.stripped())) {
            return null;
        }
        return line.stripped();
    }

    private static int lastIndex(List<Line> lines, int exclusiveEnd, Line fallback) {
        int last = exclusiveEnd - 1;
        return last >= 0 && last < lines.size() ? lines.get(last).index() : fallback.index();
    }

    private static boolean mentionsCredential(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return CREDENTIAL_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private static String removeDate(String text) {
        return DateRangeParser.findRawIn(text)
                .map(raw -> text.replace(raw, " "))
                .orElse(text)
                .replaceAll("\\s{2,}", "   ")
                .strip();
    }

    private static List<String> splitStrong(String text) {
        return java.util.Arrays.stream(STRONG_SEPARATOR.split(text))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();
    }
}
