import { describe, expect, it } from 'vitest';
import { alertBanner, alertCode, regionItems, shortName, topLevel, type AirAlert, type AlertLoc } from '../../src/features/alerts';

const now = new Date(2026, 10, 23, 14, 30);
const at = (h: number, m: number, day = 23) => new Date(2026, 10, day, h, m).toISOString();
const kyiv = (status: AirAlert['status'], since: string | null = at(14, 5), checkedAt: string | null = at(14, 29)): AirAlert =>
  ({ uid: 31, name: 'м. Київ', kind: 'city', status, since, checkedAt });

describe('плашка тривоги (як в Android)', () => {
  it('тривога: назва, з якого часу, станом на, укриття', () => {
    expect(alertBanner(kyiv('A'), now)).toEqual({
      active: true,
      title: '🚨 Повітряна тривога · м. Київ',
      detail: 'з 14:05 · станом на 14:29. Пройдіть в укриття.',
    });
  });
  it('у частині — спокійніше, без «укриття»', () => {
    expect(alertBanner(kyiv('P'), now)).toEqual({ active: false, title: '⚠️ Тривога в частині: м. Київ', detail: 'з 14:05 · станом на 14:29' });
  });
  it('немає тривоги, стан невідомий або місце не обране — плашки немає', () => {
    expect(alertBanner(kyiv('N'), now)).toBeNull();
    expect(alertBanner(kyiv(null), now)).toBeNull();
    expect(alertBanner(null, now)).toBeNull();
  });
  it('інший день — з датою, щоб старі дані не видавались за поточні', () => {
    const b = alertBanner(kyiv('A', at(23, 50, 22), at(23, 55, 22)), now)!;
    expect(b.detail).toMatch(/^з 22 .+, 23:50 · станом на 22 .+, 23:55\. Пройдіть в укриття\.$/);
  });
  it('без часу — лише укриття', () => {
    expect(alertBanner(kyiv('A', null, null), now)!.detail).toBe('Пройдіть в укриття.');
  });
  it('невідомий код сервера — «немає», null — невідомо', () => {
    expect(alertCode('A')).toBe('A');
    expect(alertCode('X')).toBe('N');
    expect(alertCode(null)).toBeNull();
  });
});

const loc = (uid: number, name: string, kind: AlertLoc['kind'], raion_uid: number | null = null): AlertLoc =>
  ({ uid, name, kind, oblast_uid: kind === 'oblast' || kind === 'city' ? null : 14, raion_uid });
const kids = [
  loc(100, 'Фастівський район', 'raion'),
  loc(101, 'Білоцерківський район', 'raion'),
  loc(110, 'Фастівська територіальна громада', 'hromada', 100),
  loc(111, 'Боярська територіальна громада', 'hromada', 100),
  loc(120, 'Білоцерківська територіальна громада', 'hromada', 101),
  loc(121, 'Узинська територіальна громада', 'hromada', 101),
];

describe('вибір місця', () => {
  it('спершу окремі міста, далі області за українською абеткою (І не після Х), без районів', () => {
    const top = topLevel([loc(1, 'Херсонська область', 'oblast'), loc(2, 'Івано-Франківська область', 'oblast'), loc(31, 'м. Київ', 'city'), kids[0]!]);
    expect(top.map((l) => l.uid)).toEqual([31, 2, 1]);
  });
  it('райони за абеткою, під кожним — його громади', () => {
    expect(regionItems(kids, '').map((i) => `${i.raion ? 'Р' : 'Г'}${i.loc.uid}`)).toEqual(['Р101', 'Г120', 'Г121', 'Р100', 'Г111', 'Г110']);
  });
  it('знайдено район — з усіма громадами', () => {
    expect(regionItems(kids, 'фастівський').map((i) => i.loc.uid)).toEqual([100, 111, 110]);
  });
  it('знайдено громаду — лише вона з її районом, регістр і пробіли не важать', () => {
    expect(regionItems(kids, '  УЗИН ').map((i) => i.loc.uid)).toEqual([101, 121]);
  });
  it('нічого не знайдено — порожньо', () => {
    expect(regionItems(kids, 'Львів')).toEqual([]);
  });
  it('коротша назва громади', () => {
    expect(shortName('Боярська територіальна громада')).toBe('Боярська громада');
    expect(shortName('Фастівський район')).toBe('Фастівський район');
  });
});
