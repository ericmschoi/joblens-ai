import type { StepId } from '../session/WizardState';
import styles from './Components.module.css';

const STEPS: { id: StepId; label: string }[] = [
  { id: 'resume', label: 'Resume' },
  { id: 'reviewResume', label: 'Review resume' },
  { id: 'job', label: 'Job description' },
  { id: 'reviewJob', label: 'Review job description' },
  { id: 'results', label: 'Analysis' },
];

type State = 'done' | 'current' | 'todo';

/**
 * A progress bar: how far through the wizard you are, and which step you are on now.
 *
 * <p>Colour carries the shape of the answer — the bar fills as you advance — but never carries it
 * alone. A finished step is marked with a tick, the current one with a caret above its marker and
 * its own number, and every step states its position and status in text a screen reader reads even
 * though the bar itself is decorative.
 */
export function StepIndicator({ current, completed }: {
  current: StepId;
  completed: readonly StepId[];
}) {
  const currentIndex = STEPS.findIndex((step) => step.id === current);

  return (
    <div className={styles.progress}>
      <ol className={styles.steps} aria-label="Progress">
        {STEPS.map((step, index) => {
          const isCurrent = step.id === current;
          const state: State = isCurrent ? 'current'
            : completed.includes(step.id) ? 'done'
              : 'todo';

          return (
            <li
              key={step.id}
              className={`${styles.step} ${styles[state]}`}
              aria-current={isCurrent ? 'step' : undefined}
            >
              <span className={styles.stepMarker} aria-hidden="true">
                {state === 'done' ? '✓' : index + 1}
              </span>
              <span className={styles.stepLabel}>
                {step.label}
                <span className={styles.visuallyHidden}>
                  {` — step ${index + 1} of ${STEPS.length}, ${statusText(state)}`}
                </span>
              </span>
            </li>
          );
        })}
      </ol>

      {/* Five labels cannot share a phone's width. Below that width the labels give way to this one
          line, which says the same thing. Hidden from assistive technology because the list above
          already carries it. */}
      <p className={styles.currentSummary} aria-hidden="true">
        Step {currentIndex + 1} of {STEPS.length} — {STEPS[currentIndex]?.label}
      </p>
    </div>
  );
}

function statusText(state: State): string {
  switch (state) {
    case 'done':
      return 'done';
    case 'current':
      return 'current step';
    case 'todo':
      return 'not started';
  }
}
