import type { StepId } from '../session/WizardState';
import styles from './Components.module.css';

const STEPS: { id: StepId; label: string }[] = [
  { id: 'resume', label: '1. Resume' },
  { id: 'reviewResume', label: '2. Review resume' },
  { id: 'job', label: '3. Job description' },
  { id: 'reviewJob', label: '4. Review job description' },
  { id: 'results', label: '5. Analysis' },
];

/** Where the user is, stated in text rather than only in colour. */
export function StepIndicator({ current, completed }: {
  current: StepId;
  completed: readonly StepId[];
}) {
  return (
    <ol className={styles.steps} aria-label="Progress">
      {STEPS.map((step) => {
        const isCurrent = step.id === current;
        const isDone = completed.includes(step.id);
        return (
          <li
            key={step.id}
            className={`${styles.stepItem} ${isCurrent ? styles.stepCurrent : ''} ${isDone ? styles.stepDone : ''}`}
            aria-current={isCurrent ? 'step' : undefined}
          >
            {step.label}
            {isDone && !isCurrent && <span> — done</span>}
          </li>
        );
      })}
    </ol>
  );
}
