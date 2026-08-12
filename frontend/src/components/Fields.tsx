import { useId, type ChangeEvent } from 'react';
import styles from './Components.module.css';

/**
 * Labelled inputs, because an input without a programmatically associated label is invisible to a
 * screen reader whatever it looks like.
 */

export function TextField({ label, value, onChange, hint }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  hint?: string;
}) {
  const id = useId();
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>{label}</label>
      {hint && <span className={styles.hint} id={`${id}-hint`}>{hint}</span>}
      <input
        id={id}
        className={styles.input}
        type="text"
        value={value}
        aria-describedby={hint ? `${id}-hint` : undefined}
        onChange={(event: ChangeEvent<HTMLInputElement>) => onChange(event.target.value)}
      />
    </div>
  );
}

export function TextAreaField({ label, value, onChange, hint, rows }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  hint?: string;
  rows?: number;
}) {
  const id = useId();
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={id}>{label}</label>
      {hint && <span className={styles.hint} id={`${id}-hint`}>{hint}</span>}
      <textarea
        id={id}
        className={styles.textarea}
        value={value}
        rows={rows ?? 8}
        aria-describedby={hint ? `${id}-hint` : undefined}
        onChange={(event: ChangeEvent<HTMLTextAreaElement>) => onChange(event.target.value)}
      />
    </div>
  );
}

export function Actions({ children }: { children: React.ReactNode }) {
  return <div className={styles.actions}>{children}</div>;
}

export function PrimaryButton({ children, onClick, disabled, type }: {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  type?: 'button' | 'submit';
}) {
  return (
    <button type={type ?? 'button'} className={styles.button} onClick={onClick} disabled={disabled}>
      {children}
    </button>
  );
}

export function SecondaryButton({ children, onClick, disabled }: {
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      className={`${styles.button} ${styles.secondary}`}
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  );
}
