import type { CategoryResult } from '../../api/types';
import { StarRating } from './StarRating';
import styles from './Results.module.css';

/**
 * One rated category, with the reasoning behind the number available but not forced on the reader.
 *
 * Native disclosure rather than a component library: the browser already handles the keyboard and
 * the announcements correctly, and the project only reaches for Radix where accessibility is
 * genuinely hard.
 */
export function CategoryCard({ result }: { result: CategoryResult }) {
  const weightPercent = Math.round(Number(result.appliedWeight) * 100);

  return (
    <article className={styles.categoryCard} aria-labelledby={`cat-${result.category}`}>
      <header className={styles.categoryHeader}>
        <h3 id={`cat-${result.category}`} className={styles.categoryTitle}>{result.displayName}</h3>
        <p className={styles.categoryScore}>
          <StarRating value={Number(result.score)} size="small" />
          <strong>{result.score} / 5.0</strong>
          <span className={styles.categoryLabel}>{result.label}</span>
        </p>
      </header>

      <p className={styles.categoryWeight}>
        {result.rated
          ? `Counts for ${weightPercent}% of the total.`
          : 'Not rated, and given no weight in the total.'}
      </p>
      <p>{result.summary}</p>

      <details className={styles.details}>
        <summary>Why this score</summary>
        <p>{result.scoreImpactExplanation}</p>

        {result.directEvidence.length > 0 && (
          <>
            <h4>What matched directly</h4>
            <ul className={styles.evidenceList}>
              {result.directEvidence.map((item, index) => (
                <li key={index}><blockquote className={styles.quote}>{item.resumeQuote}</blockquote></li>
              ))}
            </ul>
          </>
        )}

        {result.transferableEvidence.length > 0 && (
          <>
            <h4>What transfers</h4>
            <ul className={styles.evidenceList}>
              {result.transferableEvidence.map((item, index) => (
                <li key={index}><blockquote className={styles.quote}>{item.resumeQuote}</blockquote></li>
              ))}
            </ul>
          </>
        )}

        {result.gaps.length > 0 && (
          <>
            <h4>What is missing</h4>
            <ul>{result.gaps.map((gap) => <li key={gap.requirementId}>{gap.requirementText}</li>)}</ul>
          </>
        )}

        {result.unknowns.length > 0 && (
          <>
            <h4>What the resume does not say</h4>
            <p className={styles.unknownNote}>None of these has counted against you.</p>
            <ul>{result.unknowns.map((gap) => <li key={gap.requirementId}>{gap.requirementText}</li>)}</ul>
          </>
        )}

        {result.improvementSuggestions.length > 0 && (
          <>
            <h4>What would strengthen this, truthfully</h4>
            <ul>{result.improvementSuggestions.map((tip, index) => <li key={index}>{tip}</li>)}</ul>
          </>
        )}
      </details>
    </article>
  );
}
