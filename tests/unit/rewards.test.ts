import { describe, expect, it } from 'vitest';
import { DEFAULT_RATES, autoRewards, currentRun, gradeValue, levelOf, parseSettings, totals, type Challenge, type Grade, type Ledger } from '../../src/features/rewards';

let n = 0;
const g = (grade: number, date: string, subject = 'Математика', status: Grade['status'] = 'approved', amount: number | null = null): Grade =>
  ({ id: 'g' + ++n, child_id: 'k', subject, grade, date, status, amount, created_at: `${date}T10:${String(n).padStart(2, '0')}` });

describe('gradeValue', () => {
  it('використовує зафіксовану суму, інакше курс', () => {
    expect(gradeValue({ grade: 12, amount: 999 }, DEFAULT_RATES)).toBe(999);
    expect(gradeValue({ grade: 12, amount: null }, DEFAULT_RATES)).toBe(150);
    expect(gradeValue({ grade: 13, amount: null }, DEFAULT_RATES)).toBe(0);
  });
});

describe('parseSettings', () => {
  it('ігнорує сміття і затискає межі', () => {
    const s = parseSettings({ 12: '200', x: 5, streak_len: 1, streak_min: 99, streak_bonus: -5 });
    expect(s.rates[12]).toBe(200);
    expect(s.rates[1]).toBe(-100);
    expect(s.streak).toEqual({ len: 2, min: 12, bonus: 0 });
  });
  it('повертає дефолти для null', () => {
    expect(parseSettings(null).streak).toEqual({ len: 5, min: 7, bonus: 50 });
  });
});

describe('серії', () => {
  const streak = { len: 3, min: 7, bonus: 50 };
  it('нараховує бонус кожні N поспіль і скидає лічильник', () => {
    const a = [g(8, '2026-09-01'), g(9, '2026-09-02'), g(10, '2026-09-03'), g(7, '2026-09-04')];
    const r = autoRewards(a, [], streak);
    expect(r).toHaveLength(1);
    expect(r[0]!.date).toBe('2026-09-03');
    expect(currentRun(a, streak)).toBe(1);
  });
  it('низька оцінка обриває серію', () => {
    const a = [g(8, '2026-09-01'), g(3, '2026-09-02'), g(10, '2026-09-03'), g(10, '2026-09-04')];
    expect(autoRewards(a, [], streak)).toHaveLength(0);
    expect(currentRun(a, streak)).toBe(2);
  });
});

describe('челенджі', () => {
  const c: Challenge = { id: 'c1', child_id: 'k', title: '2 десятки з математики', subject: 'Математика', min_grade: 10, need: 2, reward: 100, start_date: '2026-09-01', end_date: '2026-09-07' };
  it('зараховує лише відповідні оцінки в межах дат', () => {
    const a = [g(10, '2026-08-31'), g(11, '2026-09-02', 'Англ. мова'), g(12, '2026-09-03'), g(10, '2026-09-05')];
    const r = autoRewards(a, [c], { len: 99, min: 7, bonus: 0 });
    expect(r).toEqual([expect.objectContaining({ amount: 100, date: '2026-09-05' })]);
  });
  it('не зараховує невиконаний', () => {
    expect(autoRewards([g(12, '2026-09-03')], [c], { len: 99, min: 7, bonus: 0 })).toHaveLength(0);
  });
});

describe('totals', () => {
  it('рахує періоди й баланс з виплатами', () => {
    const a = [g(12, '2026-09-21'), g(4, '2026-09-25'), g(10, '2026-08-30')];
    const l: Ledger[] = [
      { id: 'l1', child_id: 'k', kind: 'bonus', amount: 50, note: '', date: '2026-09-25', created_at: 'x' },
      { id: 'l2', child_id: 'k', kind: 'payout', amount: 100, note: '', date: '2026-09-25', created_at: 'y' },
    ];
    const t = totals(a, l, [], DEFAULT_RATES, '2026-09-25', '2026-09-21');
    expect(t.today).toBe(-20 + 50);
    expect(t.week).toBe(150 - 20 + 50);
    expect(t.month).toBe(150 - 20 + 50);
    expect(t.earned).toBe(150 - 20 + 50 + 100);
    expect(t.balance).toBe(t.earned - 100);
  });
});

it('рівні по 50 XP', () => {
  expect(levelOf(0)).toBe(1);
  expect(levelOf(49)).toBe(1);
  expect(levelOf(50)).toBe(2);
});
