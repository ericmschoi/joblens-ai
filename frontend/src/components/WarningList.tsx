import type { ExtractionWarning } from '../api/types';
import styles from './Components.module.css';

const SEVERITY_CLASS: Record<string, string> = {
  HIGH: styles.severityHigh ?? '',
  WARNING: styles.severityWarning ?? '',
  INFO: styles.severityInfo ?? '',
};

/**
 * Extraction warnings, shown above the content they are about.
 *
 * Severity is conveyed in words as well as colour, so the meaning survives for anyone who cannot
 * distinguish the colours.
 */
export function WarningList({ warnings, label }: {
  warnings: readonly ExtractionWarning[];
  label: string;
}) {
  if (warnings.length === 0) {
    return null;
  }
  return (
    <ul className={styles.warnings} aria-label={label}>
      {warnings.map((warning) => (
        <li key={warning.code} className={styles.warning}>
          <span className={`${styles.severity} ${SEVERITY_CLASS[warning.severity] ?? ''}`}>
            {warning.severity === 'HIGH' ? 'Check this' : warning.severity === 'WARNING' ? 'Warning' : 'Note'}
          </span>
          <span>
            {warning.message}
            {warning.count !== null && ` (${warning.count} found)`}
            {warning.page !== null && ` (page ${warning.page})`}
          </span>
        </li>
      ))}
    </ul>
  );
}
