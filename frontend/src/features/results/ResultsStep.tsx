import { useEffect, useRef, useState } from 'react';

import { runAnalysis } from '../../api/joblens';
import type { AnalysisResponse } from '../../api/types';
import { Actions, PrimaryButton, SecondaryButton } from '../../components/Fields';
import { WarningList } from '../../components/WarningList';
import { useWizard } from '../../session/WizardContext';
import { CategoryCard } from './CategoryCard';
import { EvidenceGroup, GapGroup } from './EvidenceGroup';
import { InterviewPreparation, OpportunityPanel, ResumePositioning } from './GuidancePanels';
import { OverallMatch } from './OverallMatch';
import { ScoreGuide } from './ScoreGuide';
import styles from './Results.module.css';

/**
 * The results page, in the order the product argues for.
 *
 * The interpretation guide comes before any individual number, the fit sections come next, and
 * opportunity value is pushed to the bottom behind a visual break because it answers a different
 * question and does not touch the score.
 */
export function ResultsStep() {
  const { state, dispatch } = useWizard();
  const [response, setResponse] = useState<AnalysisResponse | null>(null);
  const [running, setRunning] = useState(false);
  const [failed, setFailed] = useState<string | null>(null);
  const controller = useRef<AbortController | null>(null);
  const heading = useRef<HTMLHeadingElement | null>(null);

  const resume = state.confirmedResume;
  const job = state.confirmedJob;

  useEffect(() => {
    if (response) {
      heading.current?.focus();
    }
  }, [response]);

  async function analyse() {
    if (!resume || !job || running) {
      return;
    }
    setRunning(true);
    setFailed(null);
    controller.current = new AbortController();
    try {
      setResponse(await runAnalysis(resume, job, controller.current.signal));
    } catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) {
        setFailed(error instanceof Error ? error.message : 'The analysis could not be completed.');
      }
    } finally {
      setRunning(false);
    }
  }

  if (!resume || !job) {
    return null;
  }

  if (!response) {
    return (
      <section aria-labelledby="run-analysis-heading">
        <h2 id="run-analysis-heading">Ready to analyse</h2>
        <p>
          Both documents are confirmed. The analysis compares them and explains every rating it gives.
        </p>
        {failed && (
          <p role="alert">
            {failed} Try again, or go back and check the extracted documents.
          </p>
        )}
        <Actions>
          <PrimaryButton onClick={() => void analyse()} disabled={running}>
            {running ? 'Analysing…' : 'Analyse the fit'}
          </PrimaryButton>
          {running && (
            <SecondaryButton onClick={() => controller.current?.abort()}>Cancel</SecondaryButton>
          )}
        </Actions>
        {running && (
          <p role="status">
            Comparing your resume with the posting. This takes a few seconds.
          </p>
        )}
      </section>
    );
  }

  const analysis = response.analysis;

  return (
    <div>
      <h2 ref={heading} tabIndex={-1} id="results-heading">Your fit analysis</h2>

      <ScoreGuide guide={response.scoreInterpretationGuide} caveat={response.scoreCaveat} />

      <OverallMatch analysis={analysis} />

      <section aria-labelledby="categories-heading">
        <h2 id="categories-heading">Ratings by category</h2>
        <div className={styles.categoryGrid}>
          {analysis.categoryResults.map((result) => (
            <CategoryCard key={result.category} result={result} />
          ))}
        </div>
      </section>

      <EvidenceGroup
        title="Strongest matches"
        description="Direct evidence from your resume, quoted as written."
        evidence={analysis.strongestMatches}
      />
      <EvidenceGroup
        title="Transferable matches"
        description="Adjacent experience that speaks to what the posting asks for."
        evidence={analysis.transferableMatches}
      />
      <GapGroup
        title="Critical gaps"
        description="Core requirements with no supporting evidence in your resume. These are what cap a score."
        gaps={analysis.criticalGaps}
      />
      <GapGroup
        title="Minor gaps"
        description="Requirements with no supporting evidence that are not central to the role."
        gaps={analysis.minorGaps}
      />
      <GapGroup
        title="Not stated in your resume"
        description="Your resume does not say either way. None of these has counted against you."
        gaps={analysis.unknownRequirements}
      />

      <ResumePositioning advice={analysis.resumePositioning} />
      <InterviewPreparation preparation={analysis.interviewPreparation} />

      <OpportunityPanel value={analysis.opportunityValue} />

      <section className={styles.limitations} aria-labelledby="limitations-heading">
        <h2 id="limitations-heading">Limitations</h2>
        <ul>
          {analysis.limitations.map((limitation, index) => <li key={index}>{limitation}</li>)}
        </ul>
        <WarningList
          warnings={[...resume.extractionWarnings, ...job.extractionWarnings]}
          label="Extraction warnings"
        />
        <p>
          Analysed with the {response.analysisMetadata.providerId} provider, prompt{' '}
          {response.analysisMetadata.promptVersion}.{' '}
          {response.analysisMetadata.droppedEvidenceCount > 0 && (
            <>
              {response.analysisMetadata.droppedEvidenceCount} quoted item
              {response.analysisMetadata.droppedEvidenceCount === 1 ? ' was' : 's were'} discarded for
              not appearing in your resume.
            </>
          )}
        </p>
      </section>

      <Actions>
        <SecondaryButton onClick={() => dispatch({ type: 'startedOver' })}>
          Start over with a different role
        </SecondaryButton>
      </Actions>
    </div>
  );
}
