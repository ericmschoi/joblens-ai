import type { FitAnalysis } from '../../api/types';
import { StarRating } from './StarRating';
import styles from './Results.module.css';

const TIER_MEANING: Record<string, string> = {
  REACH: 'Core experience connects, but there are meaningful differences in level, scale or a specific technology.',
  TARGET: 'Your documented stack, responsibilities and level line up with this role.',
  SAFETY: 'You meet or exceed most core requirements, and the role sits at or below your documented level.',
};

const RECOMMENDATION_LABEL: Record<string, string> = {
  STRONG_APPLY: 'Strong apply',
  APPLY: 'Apply',
  CONDITIONAL: 'Apply with conditions',
  LOW_PRIORITY: 'Low priority',
};

/** The headline, with every qualifier that belongs next to it rather than buried below. */
export function OverallMatch({ analysis }: { analysis: FitAnalysis }) {
  return (
    <section className={styles.overall} aria-labelledby="overall-heading">
      <h2 id="overall-heading">Overall match</h2>

      <div className={styles.headline}>
        <StarRating value={Number(analysis.totalMatchScore)} />
        <p className={styles.headlineScore}>
          <strong>{analysis.totalMatchScore} / 5.0</strong> — {analysis.totalMatchLabel}
        </p>
      </div>

      <dl className={styles.badges}>
        <div className={styles.badge}>
          <dt>Application tier</dt>
          <dd>
            <strong>{analysis.applicationTier}</strong>
            <span className={styles.badgeNote}>{TIER_MEANING[analysis.applicationTier]}</span>
          </dd>
        </div>
        <div className={styles.badge}>
          <dt>Recommendation</dt>
          <dd>
            <strong>{RECOMMENDATION_LABEL[analysis.recommendation] ?? analysis.recommendation}</strong>
            <span className={styles.badgeNote}>
              This is advice about where to spend your effort, not a prediction about being hired.
            </span>
          </dd>
        </div>
        <div className={styles.badge}>
          <dt>Analysis confidence</dt>
          <dd>
            <strong>{analysis.scoreConfidence}</strong>
            <span className={styles.badgeNote}>
              How much to trust this reading, based on how well the documents were read.
            </span>
          </dd>
        </div>
        <div className={styles.badge}>
          <dt>Eligibility</dt>
          <dd>
            <strong>{analysis.eligibility}</strong>
            <span className={styles.badgeNote}>
              {analysis.eligibility === 'UNKNOWN'
                ? 'Nothing in the documents settles this either way, so it has not affected the score.'
                : 'Based on a condition the posting states and the documents settle.'}
            </span>
          </dd>
        </div>
      </dl>

      {analysis.scoringAdjustments.length > 0 && (
        <div className={styles.capNotice} role="note">
          <h3>Why this score is capped</h3>
          <ul>
            {analysis.scoringAdjustments.map((adjustment) => (
              <li key={adjustment.ruleId}>
                {adjustment.description}{' '}
                <span className={styles.capNumbers}>
                  The weighted total was {adjustment.scoreBeforeAdjustment}; it is shown as{' '}
                  {adjustment.scoreAfterAdjustment}.
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className={styles.feedback}>
        <h3>What this comes down to</h3>
        <p>{analysis.finalRationale}</p>
        <p><strong>{analysis.roleAlignment.headline}.</strong> {analysis.roleAlignment.detail}</p>
        <p><strong>{analysis.seniorityAlignment.headline}.</strong> {analysis.seniorityAlignment.detail}</p>
        <p>
          <strong>{analysis.realisticCompetitiveness.headline}.</strong>{' '}
          {analysis.realisticCompetitiveness.detail}
        </p>
      </div>
    </section>
  );
}
