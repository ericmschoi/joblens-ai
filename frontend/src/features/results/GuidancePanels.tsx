import type { FitAnalysis } from '../../api/types';
import styles from './Results.module.css';

/** Advice about presenting what is already there. Nothing here invents experience. */
export function ResumePositioning({ advice }: { advice: FitAnalysis['resumePositioning'] }) {
  return (
    <section aria-labelledby="positioning-heading">
      <h2 id="positioning-heading">Positioning your resume for this role</h2>
      <p>
        Every suggestion below is about reordering, emphasising or rewording what your resume already
        says. None of them adds experience you do not have.
      </p>

      <List title="Lead with" items={advice.reorderSuggestions} />
      <List title="Emphasise" items={advice.emphasisSuggestions} />
      <List title="Say less about" items={advice.deemphasizeSuggestions} />

      {advice.terminologyAlignment.length > 0 && (
        <>
          <h3>Match the posting's wording where it is honest to</h3>
          <ul>
            {advice.terminologyAlignment.map((item, index) => (
              <li key={index}>
                <strong>{item.postingTerm}</strong> — {item.rationale}
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

/** What to prepare, drawn from the two documents rather than invented. */
export function InterviewPreparation({ preparation }: {
  preparation: FitAnalysis['interviewPreparation'];
}) {
  return (
    <section aria-labelledby="interview-heading">
      <h2 id="interview-heading">Preparing for the interview</h2>

      {preparation.likelyQuestions.length > 0 && (
        <>
          <h3>Questions you are likely to be asked</h3>
          <ul>
            {preparation.likelyQuestions.map((item, index) => (
              <li key={index}>
                <strong>{item.question}</strong>
                <br />
                <span className={styles.muted}>{item.whyAsked}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      <List title="Points worth making" items={preparation.talkingPoints} />

      {preparation.gapsToExplain.length > 0 && (
        <>
          <h3>Be ready to talk about</h3>
          <ul>
            {preparation.gapsToExplain.map((item, index) => (
              <li key={index}>
                <strong>{item.gap}</strong> — {item.suggestedFraming}
              </li>
            ))}
          </ul>
        </>
      )}

      <List title="Questions to ask them" items={preparation.questionsToAsk} />
    </section>
  );
}

/**
 * How attractive the opportunity might be — a different question from how well you fit it, and
 * separated visually for that reason. None of this touches the score.
 */
export function OpportunityPanel({ value }: { value: FitAnalysis['opportunityValue'] }) {
  const entries = [
    ['Career growth', value.careerGrowth],
    ['Compensation', value.compensation],
    ['Company outlook', value.companyOutlook],
  ] as const;

  return (
    <section className={styles.opportunity} aria-labelledby="opportunity-heading">
      <h2 id="opportunity-heading">About the opportunity itself</h2>
      <p>
        This is a separate question from how well you fit the role, and none of it has affected the
        rating above.
      </p>
      <dl className={styles.opportunityList}>
        {entries.map(([label, assessment]) => (
          <div key={label} className={styles.opportunityItem}>
            <dt>{label}</dt>
            <dd>
              <strong>{assessment.rating}</strong>
              <span className={styles.muted}>
                {assessment.basis === 'INFERRED_FROM_POSTING' && ' (inferred from the posting)'}
                {assessment.basis === 'STATED_IN_POSTING' && ' (stated in the posting)'}
                {assessment.basis === 'NOT_AVAILABLE' && ' (not available)'}
              </span>
              <p>{assessment.explanation}</p>
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function List({ title, items }: { title: string; items: readonly string[] }) {
  if (items.length === 0) {
    return null;
  }
  return (
    <>
      <h3>{title}</h3>
      <ul>{items.map((item, index) => <li key={index}>{item}</li>)}</ul>
    </>
  );
}
