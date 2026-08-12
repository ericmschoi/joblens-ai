import type { EvidenceMatch, RequirementGap } from '../../api/types';
import styles from './Results.module.css';

/** Quoted evidence, always with where it came from, because a quote without a source is a claim. */
export function EvidenceGroup({ title, description, evidence }: {
  title: string;
  description: string;
  evidence: readonly EvidenceMatch[];
}) {
  if (evidence.length === 0) {
    return null;
  }
  return (
    <section aria-labelledby={`${title.replace(/\s+/g, '-').toLowerCase()}-heading`}>
      <h2 id={`${title.replace(/\s+/g, '-').toLowerCase()}-heading`}>{title}</h2>
      <p>{description}</p>
      <ul className={styles.evidenceList}>
        {evidence.map((item, index) => (
          <li key={`${item.requirementId}-${index}`} className={styles.evidenceItem}>
            <blockquote className={styles.quote}>{item.resumeQuote}</blockquote>
            <p className={styles.evidenceMeta}>
              {item.sourceLocator && <span>From {item.sourceLocator}. </span>}
              {item.rationale}
            </p>
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * Gaps and unknowns, kept in separate lists on purpose.
 *
 * A gap is something the resume shows you do not have. An unknown is something it does not say. The
 * product refuses to blur them in the arithmetic, so it will not blur them on the page either.
 */
export function GapGroup({ title, description, gaps }: {
  title: string;
  description: string;
  gaps: readonly RequirementGap[];
}) {
  if (gaps.length === 0) {
    return null;
  }
  return (
    <section aria-labelledby={`${title.replace(/\s+/g, '-').toLowerCase()}-heading`}>
      <h2 id={`${title.replace(/\s+/g, '-').toLowerCase()}-heading`}>{title}</h2>
      <p>{description}</p>
      <ul className={styles.gapList}>
        {gaps.map((gap) => (
          <li key={gap.requirementId} className={styles.gapItem}>
            <p className={styles.gapText}>
              <strong>{gap.requirementText}</strong>
              <span className={styles.gapTags}>
                {gap.importance === 'REQUIRED' ? 'Required' : 'Preferred'}
                {gap.criticality === 'CORE' && ' · Core to the role'}
              </span>
            </p>
            <p>{gap.whyItMatters}</p>
            <p><em>{gap.suggestedMitigation}</em></p>
          </li>
        ))}
      </ul>
    </section>
  );
}
