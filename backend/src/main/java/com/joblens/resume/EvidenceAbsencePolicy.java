package com.joblens.resume;

/**
 * What the absence of evidence in a resume representation is allowed to mean.
 *
 * <p>Not finding a skill can mean the candidate lacks it, or it can mean the parser missed it. Those
 * are different conclusions and only one of them may lower a score.
 */
public enum EvidenceAbsencePolicy {

    /**
     * The representation is trustworthy enough that a missing requirement may be judged {@code GAP}
     * and may trigger a critical-gap ceiling.
     */
    MAY_BE_GAP,

    /**
     * The representation is unreviewed or structurally uncertain. A missing requirement must be
     * reported as {@code UNKNOWN}: it may not be judged {@code GAP} and may not trigger a ceiling.
     */
    MUST_BE_UNKNOWN
}
