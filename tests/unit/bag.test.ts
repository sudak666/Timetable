import { describe, expect, it } from 'vitest';
import { bagFor, eveningMessage, isoWeekday, tasks, type TimetableJson } from '../../supabase/functions/school-push/bag.ts';

const L = (...s: string[]) => s.map((x) => ({ s: x }));
const row = (over: Partial<TimetableJson> = {}): TimetableJson => ({
  child_id: 'k1',
  week: [L('Математика', 'Укр. мова', 'Математика'), L('Фізика'), L('Хімія'), L('Історія'), L('Англ. мова')],
  holidays: [['2026-10-26', '2026-11-01']],
  activities: [
    { t: 'Програмування', e: '💻', d: [2], a: '18:00', b: '19:00', n: 'ноутбук' },
    { t: 'Футбол', e: '⚽', d: [2, 4], a: '9:00', b: '10:30', n: 'форма, бутси' },
    { t: 'Шахи', e: '♟️', d: [2], a: '15:00', b: '16:00', n: '' },
  ],
  ...over,
});

describe('рюкзак на завтра', () => {
  it('день тижня: 2026-11-24 — вівторок', () => expect(isoWeekday('2026-11-24')).toBe(2));
  it('уроки дня без повторів і речі на заняття за часом; без «що взяти» — не в рюкзаку', () => {
    expect(bagFor(row(), '2026-11-23')).toEqual({ subjects: ['Математика', 'Укр. мова'], things: [] });
    expect(bagFor(row(), '2026-11-24')).toEqual({
      subjects: ['Фізика'],
      things: [{ e: '⚽', t: 'Футбол', n: 'форма, бутси' }, { e: '💻', t: 'Програмування', n: 'ноутбук' }],
    });
  });
  it('канікули: уроків немає, а заняття є (як у застосунках)', () => {
    expect(bagFor(row(), '2026-10-27')).toEqual({ subjects: [], things: [{ e: '⚽', t: 'Футбол', n: 'форма, бутси' }, { e: '💻', t: 'Програмування', n: 'ноутбук' }] });
  });
  it('вихідні — порожньо; субота з уроками — є', () => {
    expect(bagFor(row(), '2026-11-29')).toEqual({ subjects: [], things: [] });
    expect(bagFor(row({ week: [...(row().week as unknown[]), L('Фіз-ра')] }), '2026-11-28').subjects).toEqual(['Фіз-ра']);
  });
  it('кривий JSON не валить нагадування', () => {
    expect(bagFor({ child_id: 'k', week: 'x', holidays: null, activities: [null, 5, { d: 'x' }] }, '2026-11-24')).toEqual({ subjects: [], things: [] });
  });
});

describe('вечірнє сповіщення', () => {
  it('рюкзак + речі + домашка — одним сповіщенням', () => {
    expect(eveningMessage({ subjects: ['Фізика', 'Хімія'], things: [{ e: '⚽', t: 'Футбол', n: 'форма' }] }, ['Фізика', 'Фізика', 'Хімія'])).toEqual({
      title: '🎒 Збери рюкзак на завтра',
      body: 'Фізика, Хімія\n⚽ Футбол: форма\n📝 Домашка: Фізика, Хімія',
      kind: 'bag',
    });
  });
  it('без розкладу — звичне нагадування про домашку', () => {
    expect(eveningMessage({ subjects: [], things: [] }, ['Фізика', 'Хімія'])).toEqual({ title: '📝 На завтра 2 завдання', body: 'Фізика, Хімія', kind: 'hw' });
  });
  it('нічого — без сповіщення', () => expect(eveningMessage({ subjects: [], things: [] }, [])).toBeNull());
  it('«завдання» / «завдань»', () => {
    expect([1, 2, 4, 5, 11, 14, 21, 22, 25].map((n) => `${n} ${tasks(n)}`)).toEqual([
      '1 завдання', '2 завдання', '4 завдання', '5 завдань', '11 завдань', '14 завдань', '21 завдання', '22 завдання', '25 завдань',
    ]);
  });
});
