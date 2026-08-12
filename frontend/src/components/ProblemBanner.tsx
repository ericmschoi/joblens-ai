import type { ProblemDetail } from '../api/problem';
import styles from './Components.module.css';

/**
 * How every failure reaches the user.
 *
 * The recovery action is given the same prominence as the problem, because a message that only
 * says what went wrong leaves someone stuck. The trace id is shown so a support conversation has
 * something to refer to that is not the document.
 */
export function ProblemBanner({ problem, onDismiss }: {
  problem: ProblemDetail;
  onDismiss: () => void;
}) {
  return (
    <div className={styles.problem} role="alert">
      <h2 className={styles.problemTitle}>{problem.title}</h2>
      <p className={styles.problemDetail}>{problem.detail}</p>
      <p className={styles.problemRecovery}>{problem.recoveryAction}</p>
      {problem.fieldErrors.length > 0 && (
        <ul>
          {problem.fieldErrors.map((error) => (
            <li key={error.field}>{error.message}</li>
          ))}
        </ul>
      )}
      {problem.traceId && <p className={styles.problemTrace}>Reference: {problem.traceId}</p>}
      <div className={styles.actions}>
        <button type="button" className={`${styles.button} ${styles.secondary}`} onClick={onDismiss}>
          Dismiss
        </button>
      </div>
    </div>
  );
}
