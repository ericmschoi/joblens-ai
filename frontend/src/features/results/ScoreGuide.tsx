import type { AnalysisResponse } from '../../api/types';
import styles from './Results.module.css';

/**
 * What the numbers mean, shown before any of them.
 *
 * Deliberately first on the page. A reader who meets "3.6" before knowing what the product means by
 * 3.6 will supply their own scale, and it will not be this one.
 */
export function ScoreGuide({ guide, caveat }: {
  guide: AnalysisResponse['scoreInterpretationGuide'];
  caveat: string;
}) {
  return (
    <section className={styles.guide} aria-labelledby="score-guide-heading">
      <h2 id="score-guide-heading">How to read these ratings</h2>
      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <caption className={styles.visuallyHidden}>
            Rating bands and what each one means
          </caption>
          <thead>
            <tr>
              <th scope="col">Rating</th>
              <th scope="col">Label</th>
              <th scope="col">Meaning</th>
            </tr>
          </thead>
          <tbody>
            {guide.map((band) => (
              <tr key={band.label}>
                <td>{band.from}–{band.to}</td>
                <td>{band.label}</td>
                <td>{band.meaning}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className={styles.caveat}>{caveat}</p>
    </section>
  );
}
