// @vitest-environment node
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * JobLens is an English-only product. Korean is used for development discussion only and must never
 * reach the user interface, so the source tree is scanned for Hangul and CJK characters.
 *
 * This guard runs over the whole frontend source tree rather than a single component, so new
 * screens are covered automatically.
 */

const SRC_ROOT = resolve(process.cwd(), 'src');
const SCANNED_EXTENSIONS = ['.ts', '.tsx', '.css'];

// Hangul syllables and jamo, CJK ideographs, and Japanese kana.
// Written with escapes so that this guard never matches its own source.
const NON_ENGLISH = new RegExp(
  '[\\u1100-\\u11FF\\u3040-\\u30FF\\u3130-\\u318F\\u4E00-\\u9FFF\\uAC00-\\uD7A3]',
  'u',
);

function listSourceFiles(directory: string): string[] {
  return readdirSync(directory).flatMap((entry) => {
    const fullPath = join(directory, entry);
    if (statSync(fullPath).isDirectory()) {
      return listSourceFiles(fullPath);
    }
    return SCANNED_EXTENSIONS.some((extension) => entry.endsWith(extension)) ? [fullPath] : [];
  });
}

describe('user-facing copy', () => {
  const files = listSourceFiles(SRC_ROOT);

  it('finds source files to scan', () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it.each(files)('%s contains only English text', (file) => {
    const contents = readFileSync(file, 'utf8');
    const match = NON_ENGLISH.exec(contents);

    expect(
      match,
      match ? `Found non-English character "${match[0]}" at index ${String(match.index)}` : '',
    ).toBeNull();
  });
});
