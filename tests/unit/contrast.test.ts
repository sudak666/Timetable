import { describe, expect, it } from 'vitest';
import { SUBJECTS } from '../../src/data/schedule';
import { gradeColor, THEMES } from '../../src/features/rewards';

/** WCAG 2.2: відносна яскравість і контраст. */
const lum = (hex: string) => {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * c[0]! + 0.7152 * c[1]! + 0.0722 * c[2]!;
};
const contrast = (a: string, b: string) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x! + 0.05) / (y! + 0.05); };

describe('WCAG AA: білий текст на кольорових плашках ≥ 4.5:1', () => {
  it.each(Object.entries(SUBJECTS))('%s', (_, v) => expect(contrast(v.color, '#ffffff')).toBeGreaterThanOrEqual(4.5));
  it.each([12, 9, 5, 2])('оцінка %i', (gr) => expect(contrast(gradeColor(gr), '#ffffff')).toBeGreaterThanOrEqual(4.5));
  it.each(THEMES.map(([c]) => c))('акцент %s', (c) => expect(contrast(c, '#ffffff')).toBeGreaterThanOrEqual(4.5));
});

it('приглушений текст на картках ≥ 4.5:1 (світла й темна тема)', () => {
  expect(contrast('#5f6384', '#ffffff')).toBeGreaterThanOrEqual(4.5);
  expect(contrast('#5f6384', '#f0f1fa')).toBeGreaterThanOrEqual(4.5);
  expect(contrast('#a3a6cc', '#1a1b36')).toBeGreaterThanOrEqual(4.5);
});
