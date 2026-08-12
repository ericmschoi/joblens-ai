import { ProblemBanner } from '../components/ProblemBanner';
import { StepIndicator } from '../components/StepIndicator';
import { JobDescriptionStep } from '../features/jobinput/JobDescriptionStep';
import { JobReviewStep } from '../features/review/JobReviewStep';
import { ResumeReviewStep } from '../features/review/ResumeReviewStep';
import { ResultsStep } from '../features/results/ResultsStep';
import { ResumeUploadStep } from '../features/upload/ResumeUploadStep';
import { WizardProvider, useWizard } from '../session/WizardContext';
import type { StepId } from '../session/WizardState';
import styles from './App.module.css';

function completedSteps(state: ReturnType<typeof useWizard>['state']): StepId[] {
  const done: StepId[] = [];
  if (state.resumeExtraction) {
    done.push('resume');
  }
  if (state.confirmedResume) {
    done.push('reviewResume');
  }
  if (state.jobExtraction) {
    done.push('job');
  }
  if (state.confirmedJob) {
    done.push('reviewJob');
  }
  return done;
}

function CurrentStep() {
  const { state } = useWizard();

  switch (state.step) {
    case 'resume':
      return <ResumeUploadStep />;
    case 'reviewResume':
      return <ResumeReviewStep />;
    case 'job':
      return <JobDescriptionStep />;
    case 'reviewJob':
      return <JobReviewStep />;
    case 'results':
      return <ResultsStep />;
  }
}

function Wizard() {
  const { state, dispatch } = useWizard();

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>JobLens AI</h1>
        <p className={styles.tagline}>
          Compare a resume with a job posting and get an evidence-based fit analysis you can trace
          back to the documents.
        </p>
      </header>

      <main className={styles.main}>
        <StepIndicator current={state.step} completed={completedSteps(state)} />

        {state.problem && (
          <ProblemBanner
            problem={state.problem}
            onDismiss={() => dispatch({ type: 'dismissedProblem' })}
          />
        )}

        <CurrentStep />

        <p className={styles.note}>
          JobLens evaluates experience that is explicitly documented in the resume. Nothing you
          upload is stored: closing this tab ends the session and the documents go with it.
        </p>
      </main>
    </div>
  );
}

export default function App() {
  return (
    <WizardProvider>
      <Wizard />
    </WizardProvider>
  );
}
