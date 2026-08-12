import styles from './Results.module.css';

/**
 * A rating drawn to the decimal, not rounded to the nearest half.
 *
 * The number is what is authoritative; the stars exist so the shape of a result can be taken in at
 * a glance. Rounding 4.7 to four and a half stars would make the picture disagree with the figure
 * beside it, so the fifth star is clipped to exactly seventy percent.
 *
 * The accessible name carries the value, and the value is always printed as text next to it, so
 * nothing here depends on seeing colour or shape.
 */
export function StarRating({ value, size = 'large' }: { value: number; size?: 'large' | 'small' }) {
  const clamped = Math.max(0, Math.min(5, value));
  const percent = (clamped / 5) * 100;
  const label = `${clamped.toFixed(1)} out of 5`;

  return (
    <span className={size === 'large' ? styles.starsLarge : styles.starsSmall}>
      <svg
        viewBox="0 0 100 20"
        role="img"
        aria-label={label}
        className={styles.starsSvg}
        preserveAspectRatio="xMinYMid meet"
      >
        <defs>
          <clipPath id={`fill-${percent.toFixed(2).replace('.', '-')}`}>
            <rect x="0" y="0" width={percent} height="20" />
          </clipPath>
        </defs>
        <g className={styles.starTrack} aria-hidden="true">
          {[0, 1, 2, 3, 4].map((index) => (
            <Star key={index} x={index * 20} />
          ))}
        </g>
        <g
          className={styles.starFill}
          clipPath={`url(#fill-${percent.toFixed(2).replace('.', '-')})`}
          aria-hidden="true"
        >
          {[0, 1, 2, 3, 4].map((index) => (
            <Star key={index} x={index * 20} />
          ))}
        </g>
      </svg>
    </span>
  );
}

function Star({ x }: { x: number }) {
  const points = '10,1.6 12.5,7.2 18.4,7.9 14,11.9 15.2,17.8 10,14.9 4.8,17.8 6,11.9 1.6,7.9 7.5,7.2';
  return <polygon points={points} transform={`translate(${x} 0)`} />;
}
