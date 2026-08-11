import '@testing-library/jest-dom/vitest';

import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Testing Library only registers its own cleanup when Vitest globals are enabled. This project uses
// explicit imports instead, so unmounting has to be wired up here. Without it, renders accumulate in
// the document and produce duplicate landmarks and duplicate text matches.
afterEach(cleanup);
