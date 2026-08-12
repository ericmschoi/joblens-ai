package com.joblens.analysis.validate;

import com.joblens.analysis.model.AnalysisDraft;
import com.joblens.analysis.model.Assessment;
import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.error.ApiException;
import com.joblens.error.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns whatever a provider returned into a draft, or refuses it.
 *
 * <p>Model output is untrusted input. It is parsed strictly — an unknown field is a failure, which
 * is how a provider that tried to return a score gets rejected — and then checked against the
 * invariants the scorer depends on. Anything that fails becomes a clean error with a recovery
 * action, never a partially valid result quietly passed downstream.
 */
@Component
public class AnalysisDraftValidator {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisDraftValidator.class);

    private static final int MAX_REQUIREMENTS = 120;

    /** Parsing is strict on purpose: extra fields mean the provider is not speaking this contract. */
    private final JsonMapper strictMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * @param absentEvidenceMustBeUnknown when true, any {@code GAP} the model returned is downgraded
     *        to {@code UNKNOWN}. The rule is enforced here rather than trusted to the prompt,
     *        because a prompt is a request and this is a guarantee.
     */
    public AnalysisDraft validate(String rawJson, boolean absentEvidenceMustBeUnknown) {
        AnalysisDraft draft = parse(rawJson);

        requireSchemaVersion(draft);
        requireUsableRequirements(draft);
        requireEvidenceBelongsToItsRequirement(draft);
        requireCompanyOutlookIsNotInvented(draft);

        return absentEvidenceMustBeUnknown ? downgradeGaps(draft) : draft;
    }

    private AnalysisDraft parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw invalid("empty-response");
        }
        try {
            return strictMapper.readValue(rawJson.strip(), AnalysisDraft.class);
        } catch (RuntimeException e) {
            // The parser message can quote the payload, which quotes the documents. Only the type.
            throw invalid(e.getClass().getSimpleName());
        }
    }

    private static void requireSchemaVersion(AnalysisDraft draft) {
        if (!AnalysisDraft.SCHEMA_VERSION.equals(draft.schemaVersion())) {
            throw invalid("schema-version");
        }
    }

    private static void requireUsableRequirements(AnalysisDraft draft) {
        List<RequirementAssessment> assessments = draft.requirementAssessments();
        if (assessments.isEmpty() || assessments.size() > MAX_REQUIREMENTS) {
            throw invalid("requirement-count");
        }
        Set<String> ids = new HashSet<>();
        for (RequirementAssessment assessment : assessments) {
            if (assessment.id() == null || assessment.id().isBlank() || !ids.add(assessment.id())) {
                throw invalid("requirement-id");
            }
            if (assessment.requirementText() == null || assessment.requirementText().isBlank()) {
                throw invalid("requirement-text");
            }
            if (assessment.status() == null || assessment.importance() == null
                    || assessment.criticality() == null || assessment.primaryCategory() == null) {
                throw invalid("requirement-fields");
            }
        }
    }

    private static void requireEvidenceBelongsToItsRequirement(AnalysisDraft draft) {
        for (RequirementAssessment assessment : draft.requirementAssessments()) {
            boolean mismatched = assessment.evidence().stream()
                    .anyMatch(evidence -> !assessment.id().equals(evidence.requirementId()));
            if (mismatched) {
                throw invalid("evidence-requirement-mismatch");
            }
        }
    }

    /**
     * The product has no company research, so a confident outlook could only have come from the
     * model's memory. That is exactly the kind of stale claim the brief forbids.
     */
    private static void requireCompanyOutlookIsNotInvented(AnalysisDraft draft) {
        if (draft.opportunityValue() == null || draft.opportunityValue().companyOutlook() == null) {
            throw invalid("opportunity-value");
        }
        Assessment outlook = draft.opportunityValue().companyOutlook();
        if (outlook.rating() != Assessment.Rating.UNKNOWN
                || outlook.basis() != Assessment.Basis.NOT_AVAILABLE) {
            throw invalid("company-outlook-invented");
        }
    }

    private static AnalysisDraft downgradeGaps(AnalysisDraft draft) {
        List<RequirementAssessment> corrected = new ArrayList<>();
        int downgraded = 0;

        for (RequirementAssessment assessment : draft.requirementAssessments()) {
            if (assessment.status() == MatchStatus.GAP) {
                corrected.add(assessment.withStatus(MatchStatus.UNKNOWN,
                        "Reported as unknown rather than missing: the resume has not been confirmed as "
                                + "read correctly, so absent evidence cannot be treated as absent experience."));
                downgraded++;
            } else {
                corrected.add(assessment);
            }
        }
        if (downgraded == 0) {
            return draft;
        }

        LOG.info("downgraded gaps to unknown count={} reason=unconfirmed-or-uncertain-resume", downgraded);
        List<String> limitations = new ArrayList<>(draft.limitations());
        limitations.add("The resume was not confirmed as correctly read, so nothing missing from it is "
                + "reported as a gap. Confirm the extracted resume for a sharper result.");
        return draft.withRequirementAssessments(corrected).withLimitations(limitations);
    }

    private static ApiException invalid(String reason) {
        LOG.warn("code={} reason={}", ErrorCode.AI_OUTPUT_INVALID, reason);
        return new ApiException(ErrorCode.AI_OUTPUT_INVALID,
                "The analysis result did not match the expected format.");
    }
}
