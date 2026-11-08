import { describe, expect, it } from 'vitest';
import { applyTimetable } from '../../src/data/schedule';
import { clockOf, hms, iso, lessonsOn, mondayOf, nextLessonDate, nextSchoolDate, statusAt } from '../../src/lib/time';

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
  it('наступний навчальний день після пʼятниці — понеділок', () => expect(iso(nextSchoolDate(new Date(2026, 8, 25, 12)))).toBe('2026-09-28'));
  it('канікули: уроків немає', () => expect(statusAt(at('2026-10-27T09:00')).kind).toBe('done'));
  it('після канікул — понеділок 2.11', () => expect(iso(nextSchoolDate(new Date(2026, 9, 23, 12)))).toBe('2026-11-02'));
  it('домашка не припадає на канікули', () => expect(nextLessonDate('ЗБД', new Date(2026, 9, 23))).toBe('2026-11-05'));
  it('зимові: після 23.12 — 11.01', () => expect(iso(nextSchoolDate(new Date(2026, 11, 23, 12)))).toBe('2027-01-11'));
  it('наступний урок ЗБД — четвер', () => expect(nextLessonDate('ЗБД', new Date(2026, 8, 21))).toBe('2026-09-24'));
});

it('hms', () => {
  expect(hms(0.5)).toBe('0:30');
  expect(hms(75)).toBe('1:15:00');
  expect(hms(-3)).toBe('0:00');
});

describe('розклад дитини з school_timetables', () => {
  const row = {
    child_id: 'k1',
    bells: [['9:00', '9:40'], ['9:50', '10:30']] as [string, string][],
    week: [[{ s: 'Фізика' }, { s: 'Хімія', o: true }], [], [], [], []],
    holidays: [] as [string, string][],
  };
  it('підміняє дзвінки, уроки й канікули, а null повертає приклад', () => {
    applyTimetable(row);
    expect(statusAt(at('2026-10-26T09:10'))).toMatchObject({ kind: 'lesson', idx: 0 }); // понеділок; канікули прибрано
    expect(lessonsOn(new Date(2026, 9, 27))).toBeUndefined(); // вівторок порожній
    expect(nextLessonDate('Хімія', new Date(2026, 9, 26))).toBe('2026-11-02');
    applyTimetable(null);
    expect(statusAt(at('2026-10-26T09:10')).kind).toBe('done'); // знову канікули з прикладу
  });
});
