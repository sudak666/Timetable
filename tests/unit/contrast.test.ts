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

it('приглушений текст на картках і сторінці ≥ 4.5:1 (зошит, крафт, дошка)', () => {
  for (const bg of ['#ffffff', '#f3eee2', '#fbf8f0', '#e4d2ae']) expect(contrast('#4c5163', bg)).toBeGreaterThanOrEqual(4.5);
  for (const bg of ['#20302a', '#16221d']) expect(contrast('#b7c2bb', bg)).toBeGreaterThanOrEqual(4.5);
});

it('заголовки від руки кольором акценту на крафті ≥ 3:1 (великий текст)', () => {
  for (const [c] of THEMES) expect(contrast(c, '#e4d2ae')).toBeGreaterThanOrEqual(3);
});

it('картка «Зараз»: білий текст на градієнті акцент → акцент 70% + ink 30% (styles.css .now) ≥ 4.5:1', () => {
  const mix = (a: string, b: string, k: number) => '#' + [1, 3, 5].map((i) => Math.round(parseInt(a.slice(i, i + 2), 16) * k + parseInt(b.slice(i, i + 2), 16) * (1 - k)).toString(16).padStart(2, '0')).join('');
  for (const [c] of THEMES) expect(contrast(mix(c, '#1b1c33', 0.7), '#ffffff')).toBeGreaterThanOrEqual(4.5);
});
