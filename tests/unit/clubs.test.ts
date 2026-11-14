import { describe, expect, it } from 'vitest';
import type { ActivityRow } from '../../src/data/schedule';
import { addClub, blankDraft, fromRow, removeClub, setClub, toRow, toggleClubDay, validate } from '../../src/features/timetable';
import { afterSchool, clubBag, clubsOn, daysText, dueReminder } from '../../src/lib/clubs';

const football: ActivityRow = { t: 'Футбол', e: '⚽', d: [1, 6], a: '16:00', b: '17:30', n: 'форма' };
const code: ActivityRow = { t: 'Програмування', e: '💻', d: [1], a: '18:00', b: '19:00' };
const chess: ActivityRow = { t: 'Шахи', e: '♟️', d: [2], a: '15:00', b: '16:00', n: 'блокнот' };
const all = [code, football, chess];
// 2026-11-09 — понеділок.
const mon = (t: string) => new Date(`2026-11-09T${t}`);

describe('«Після школи»', () => {
  it('заняття дня за часом', () => expect(clubsOn(all, mon('09:00')).map((c) => c.t)).toEqual(['Футбол', 'Програмування']));
  it('сьогодні — поточне й наступне', () => {
    expect(afterSchool(all, mon('16:30'))).toEqual({ today: true, rows: [{ c: football, st: 'cur' }, { c: code, st: '' }] });
    expect(afterSchool(all, mon('18:10'))?.rows.map((r) => r.st)).toEqual(['past', 'cur']);
  });
  it('після останнього — завтрашні; якщо завтра нічого — null', () => {
    expect(afterSchool(all, mon('19:00'))).toEqual({ today: false, rows: [{ c: chess, st: '' }] });
    expect(afterSchool(all, new Date('2026-11-11T12:00'))).toBeNull();
  });
  it('рюкзак: сьогодні під час уроків, після — завтра з позначкою дня', () => {
    expect(clubBag(all, mon('10:00'), false, 0)).toEqual([{ c: football, day: null }]);
    expect(clubBag(all, mon('19:30'), true, 1).map((x) => x.c.t)).toEqual(['Шахи']);
    // П'ятниця ввечері, рюкзак на понеділок (0), футбол у суботу.
    expect(clubBag(all, new Date('2026-11-13T18:00'), true, 0)).toEqual([{ c: football, day: 'Сб' }]);
  });
  it('нагадування за 15 хв', () => {
    expect(dueReminder(all, mon('15:50'), 15)).toBe(football);
    expect(dueReminder(all, mon('15:40'), 15)).toBeNull();
    expect(dueReminder(all, mon('16:00'), 15)).toBeNull();
  });
  it('дні текстом', () => {
    expect(daysText([3, 1])).toBe('Пн, Ср');
    expect(daysText([1, 2, 3, 4, 5])).toBe('Пн–Пт');
    expect(daysText([1, 2, 3, 4, 5, 6, 7])).toBe('щодня');
  });
});

describe('заняття в редакторі', () => {
  it('додати з шаблону, дні, перевірка, формат сервера', () => {
    let d = addClub(blankDraft(), '⚽', 'Футбол');
    expect(validate(d)).toEqual(['Футбол: обери хоча б один день']);
    d = toggleClubDay(toggleClubDay(toggleClubDay(d, 0, 4), 0, 2), 0, 4);
    d = setClub(d, 0, { a: '7:05', b: '8:00', n: ' форма ' });
    expect(validate(d)).toEqual([]);
    expect(toRow(d, 'k', 'f').activities).toEqual([{ t: 'Футбол', e: '⚽', d: [2], a: '7:05', b: '8:00', p: null, n: 'форма' }]);
    expect(validate(setClub(d, 0, { b: '7:00' }))).toEqual(['Футбол: кінець має бути пізніше за початок']);
    expect(validate(setClub(d, 0, { t: ' ' }))).toEqual(['Заняття 1: порожня або задовга назва']);
    expect(removeClub(d, 0).clubs).toEqual([]);
  });
  it('рядок сервера → чернетка → рядок без втрат; старий рядок без занять', () => {
    const row = { child_id: 'k', bells: [['8:30', '9:15']] as [string, string][], week: [[], [], [], [], []], holidays: [], activities: [{ ...football, p: null }] };
    expect(toRow(fromRow(row), 'k', 'f').activities).toEqual([{ ...football, p: null }]);
    expect(fromRow({ child_id: row.child_id, bells: row.bells, week: row.week, holidays: row.holidays }).clubs).toEqual([]);
  });
});
