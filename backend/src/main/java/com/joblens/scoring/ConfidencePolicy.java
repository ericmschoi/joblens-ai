package com.joblens.scoring;

import com.joblens.analysis.model.MatchStatus;
import com.joblens.analysis.model.RequirementAssessment;
import com.joblens.document.ExtractionWarning;
import com.joblens.document.WarningCode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * How much the analysis itself should be trusted.
 *
 * <p>Not a second opinion about the candidate. It answers a different question: given how well the
 * documents were read, how much came back unknown, and how much quoted evidence failed
 * verification, how firm is any of this?
 */
@Component
public class ConfidencePolicy {

    private static final double HIGH_UNKNOWN_SHARE = 0.35;
    private static final double MODERATE_UNKNOWN_SHARE = 0.15;
    private static final double GROUNDING_FAILURE_LIMIT = 0.10;
    private static final int SHORT_RESUME_CHARACTERS = 1500;
    private static final int SOLID_RESUME_CHARACTERS = 2500;
    private static final int ENOUGH_REQUIREMENTS = 5;

    public com.joblens.scoring.model.ScoreConfidence decide(List<RequirementAssessment> assessments,
            List<ExtractionWarning> warnings, int resumeCharacters, double groundingFailureRatio) {

        double unknownShare = assessments.isEmpty() ? 1.0 : (double) assessments.stream()
                .filter(assessment -> assessment.status() == MatchStatus.UNKNOWN)
                .count() / assessments.size();

        boolean severeWarning = warnings.stream()
                .anyMatch(warning -> warning.severity() == WarningCode.Severity.HIGH);

        if (severeWarning || resumeCharacters < SHORT_RESUME_CHARACTERS
                || unknownShare > HIGH_UNKNOWN_SHARE
                || groundingFailureRatio > GROUNDING_FAILURE_LIMIT) {
            return com.joblens.scoring.model.ScoreConfidence.LOW;
        }
        if (resumeCharacters >= SOLID_RESUME_CHARACTERS
                && unknownShare <= MODERATE_UNKNOWN_SHARE
                && groundingFailureRatio == 0.0
                && assessments.size() >= ENOUGH_REQUIREMENTS) {
            return com.joblens.scoring.model.ScoreConfidence.HIGH;
        }
        return com.joblens.scoring.model.ScoreConfidence.MEDIUM;
    }
}
