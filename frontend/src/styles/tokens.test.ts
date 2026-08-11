// @vitest-environment node
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

/**
 * Contrast is verified against the token palette itself rather than against rendered pixels,
 * because jsdom has no paint engine and cannot evaluate the axe colour-contrast rule.
 * Every text token must clear WCAG 2.2 AA (4.5:1) on the surfaces it is used on, in both schemes.
 */

const CSS = readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf8');

function extractScheme(css: string, scheme: 'light' | 'dark'): Map<string, string> {
  const darkBlockMatch = /@media \(prefers-color-scheme: dark\) \{\s*:root \{([\s\S]*?)\}/.exec(css);
  const lightBlockMatch = /^:root \{([\s\S]*?)\}/m.exec(css);

  const block = scheme === 'dark' ? darkBlockMatch?.[1] : lightBlockMatch?.[1];
  if (block === undefined) {
    throw new Error(`Could not find the ${scheme} token block in tokens.css`);
  }

  const tokens = new Map<string, string>();
  for (const match of block.matchAll(/(--[\w-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)) {
    const [, name, value] = match;
    if (name !== undefined && value !== undefined) {
      tokens.set(name, value);
    }
  }
  return tokens;
}

function channelLuminance(channel: number): number {
  const normalised = channel / 255;
  return normalised <= 0.04045 ? normalised / 12.92 : Math.pow((normalised + 0.055) / 1.055, 2.4);
}

function relativeLuminance(hex: string): number {
  const red = Number.parseInt(hex.slice(1, 3), 16);
  const green = Number.parseInt(hex.slice(3, 5), 16);
  const blue = Number.parseInt(hex.slice(5, 7), 16);
  return (
    0.2126 * channelLuminance(red) +
    0.7152 * channelLuminance(green) +
    0.0722 * channelLuminance(blue)
  );
}

function contrastRatio(foreground: string, background: string): number {
  const a = relativeLuminance(foreground);
  const b = relativeLuminance(background);
  const lighter = Math.max(a, b);
  const darker = Math.min(a, b);
  return (lighter + 0.05) / (darker + 0.05);
}

const TEXT_PAIRS: ReadonlyArray<readonly [foreground: string, background: string]> = [
  ['--color-text', '--color-surface'],
  ['--color-text', '--color-surface-raised'],
  ['--color-text-muted', '--color-surface'],
  ['--color-text-muted', '--color-surface-raised'],
  ['--color-accent-text', '--color-accent'],
  ['--color-danger', '--color-surface'],
  ['--color-warning', '--color-surface'],
  ['--color-success', '--color-surface'],
];

describe.each(['light', 'dark'] as const)('%s colour scheme', (scheme) => {
  const tokens = extractScheme(CSS, scheme);

  it.each(TEXT_PAIRS)('%s on %s meets AA contrast for body text', (foreground, background) => {
    const foregroundValue = tokens.get(foreground);
    const backgroundValue = tokens.get(background);

    expect(foregroundValue, `${foreground} is missing`).toBeDefined();
    expect(backgroundValue, `${background} is missing`).toBeDefined();

    const ratio = contrastRatio(foregroundValue as string, backgroundValue as string);
    expect(Number(ratio.toFixed(2))).toBeGreaterThanOrEqual(4.5);
  });

  it('uses a focus indicator that meets the non-text contrast minimum', () => {
    const focus = tokens.get('--color-focus');
    const surface = tokens.get('--color-surface');

    expect(focus, '--color-focus is missing').toBeDefined();
    expect(surface, '--color-surface is missing').toBeDefined();

    const ratio = contrastRatio(focus as string, surface as string);
    expect(Number(ratio.toFixed(2))).toBeGreaterThanOrEqual(3);
  });
});
