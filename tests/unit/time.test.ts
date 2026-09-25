import { describe, expect, it } from 'vitest';
import { clockOf, hms, iso, mondayOf, nextLessonDate, nextSchoolDay, statusAt } from '../../src/lib/time';

const at = (s: string) => clockOf(new Date(s));

describe('statusAt', () => {
  it('до уроків', () => expect(statusAt(at('2026-09-21T08:00')).kind).toBe('before'));
  it('урок', () => expect(statusAt(at('2026-09-21T08:40'))).toMatchObject({ kind: 'lesson', idx: 0 }));
  it('перерва', () => expect(statusAt(at('2026-09-21T09:20'))).toMatchObject({ kind: 'break', idx: 1, prevEnd: 555 }));
  it('після уроків', () => expect(statusAt(at('2026-09-22T15:00')).kind).toBe('done'));
  it('вихідний', () => expect(statusAt(at('2026-09-26T10:00')).kind).toBe('done'));
  it('факультатив у понеділок 7-м уроком', () => expect(statusAt(at('2026-09-21T14:40'))).toMatchObject({ kind: 'lesson', idx: 6 }));
});

describe('дати', () => {
  it('iso не зсувається через UTC', () => expect(iso(new Date(2026, 8, 25, 0, 30))).toBe('2026-09-25'));
  it('понеділок тижня', () => expect(iso(mondayOf(new Date(2026, 8, 27)))).toBe('2026-09-21'));
  it('наступний навчальний день після пʼятниці — понеділок', () => expect(nextSchoolDay(4)).toBe(0));
  it('наступний урок ЗБД — четвер', () => expect(nextLessonDate('ЗБД', new Date(2026, 8, 21))).toBe('2026-09-24'));
});

it('hms', () => {
  expect(hms(0.5)).toBe('0:30');
  expect(hms(75)).toBe('1:15:00');
  expect(hms(-3)).toBe('0:00');
});
