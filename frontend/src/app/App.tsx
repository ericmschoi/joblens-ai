import styles from './App.module.css';

const STEPS = [
  {
    title: 'Upload your resume',
    description:
      'Add a text-based PDF. JobLens reads only what the file already contains, and the file is never stored.',
  },
  {
    title: 'Add the job posting',
    description: 'Provide a job URL, or paste the job description if the page cannot be read.',
  },
  {
    title: 'Review, then analyse',
    description:
      'Check and correct the extracted text first. The version you confirm is what gets analysed.',
  },
] as const;

export default function App() {
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
        <h2 className={styles.sectionTitle}>How it works</h2>
        <ol className={styles.steps}>
          {STEPS.map((step, index) => (
            <li key={step.title} className={styles.step}>
              <span className={styles.stepNumber} aria-hidden="true">
                {index + 1}
              </span>
              <h3 className={styles.stepTitle}>{step.title}</h3>
              <p className={styles.stepDescription}>{step.description}</p>
            </li>
          ))}
        </ol>

        <p className={styles.note}>
          JobLens evaluates experience that is explicitly documented in the resume. Experience that
          is not written down cannot be assessed.
        </p>
      </main>
    </div>
  );
}
