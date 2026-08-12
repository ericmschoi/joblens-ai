import type { ConfirmedJobPosting, ConfirmedResume, JobExtraction, ResumeExtraction } from '../api/types';
import type { ProblemDetail } from '../api/problem';

/**
 * The whole session, in one place.
 *
 * Nothing is persisted anywhere: no server storage, no local storage. Closing the tab ends the
 * session and the documents go with it, which is the privacy promise made concrete.
 */

export type StepId = 'resume' | 'job' | 'reviewResume' | 'reviewJob' | 'results';

export interface WizardState {
  readonly step: StepId;
  readonly busy: null | 'extractingResume' | 'extractingJob' | 'confirmingResume' | 'confirmingJob';
  readonly problem: ProblemDetail | null;
  readonly resumeExtraction: ResumeExtraction | null;
  readonly confirmedResume: ConfirmedResume | null;
  readonly jobExtraction: JobExtraction | null;
  readonly confirmedJob: ConfirmedJobPosting | null;
}

export const initialState: WizardState = {
  step: 'resume',
  busy: null,
  problem: null,
  resumeExtraction: null,
  confirmedResume: null,
  jobExtraction: null,
  confirmedJob: null,
};

export type WizardAction =
  | { type: 'started'; busy: NonNullable<WizardState['busy']> }
  | { type: 'failed'; problem: ProblemDetail }
  | { type: 'cancelled' }
  | { type: 'dismissedProblem' }
  | { type: 'resumeExtracted'; extraction: ResumeExtraction }
  | { type: 'resumeConfirmed'; confirmed: ConfirmedResume }
  | { type: 'jobExtracted'; extraction: JobExtraction }
  | { type: 'jobConfirmed'; confirmed: ConfirmedJobPosting }
  | { type: 'wentTo'; step: StepId }
  | { type: 'startedOver' };

export function wizardReducer(state: WizardState, action: WizardAction): WizardState {
  switch (action.type) {
    case 'started':
      return { ...state, busy: action.busy, problem: null };
    case 'failed':
      return { ...state, busy: null, problem: action.problem };
    case 'cancelled':
      return { ...state, busy: null };
    case 'dismissedProblem':
      return { ...state, problem: null };

    case 'resumeExtracted':
      // A new upload invalidates anything confirmed from the old one.
      return {
        ...state,
        busy: null,
        problem: null,
        resumeExtraction: action.extraction,
        confirmedResume: null,
        step: 'reviewResume',
      };
    case 'resumeConfirmed':
      return {
        ...state,
        busy: null,
        problem: null,
        confirmedResume: action.confirmed,
        step: state.confirmedJob ? 'results' : 'job',
      };

    case 'jobExtracted':
      return {
        ...state,
        busy: null,
        problem: null,
        jobExtraction: action.extraction,
        confirmedJob: null,
        step: 'reviewJob',
      };
    case 'jobConfirmed':
      return {
        ...state,
        busy: null,
        problem: null,
        confirmedJob: action.confirmed,
        step: state.confirmedResume ? 'results' : 'resume',
      };

    case 'wentTo':
      return { ...state, step: action.step, problem: null };
    case 'startedOver':
      return initialState;
  }
}
