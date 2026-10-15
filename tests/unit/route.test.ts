import { describe, expect, it } from 'vitest';
import { formatRoute, parseRoute } from '../../src/lib/route';

const T = ['today', 'grades', 'hw', 'me'] as const;
const K = '0b1c2d3e-4f50-6172-8394-a5b6c7d8e9f0';

describe('route', () => {
  it('парсить вкладку і параметри', () => {
    expect(parseRoute(`#grades?kid=${K}`, T, 'today')).toEqual({ tab: 'grades', day: null, kid: K });
    expect(parseRoute('#today?day=2', T, 'today')).toEqual({ tab: 'today', day: 2, kid: null });
  });
  it('відкидає сміття', () => {
    expect(parseRoute('#hack?day=9&kid=<x>', T, 'today')).toEqual({ tab: 'today', day: null, kid: null });
    expect(parseRoute('', T, 'today').tab).toBe('today');
  });
  it('round-trip', () => {
    for (const h of ['#today', '#today?day=0', `#hw?kid=${K}`, '#me']) expect(formatRoute(parseRoute(h, T, 'today'))).toBe(h);
  });
  it('day лише для розкладу, kid лише для інших вкладок', () => {
    expect(formatRoute({ tab: 'grades', day: 3, kid: K })).toBe(`#grades?kid=${K}`);
    expect(formatRoute({ tab: 'today', day: 3, kid: K })).toBe('#today?day=3');
  });
});
