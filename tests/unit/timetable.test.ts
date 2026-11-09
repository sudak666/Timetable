import { describe, expect, it } from 'vitest';
import {
  MAX_LESSONS, addBell, addHoliday, addLesson, blankDraft, fromRow, inputTime, lessonsLostByLastBell, moveLesson, normTime,
  removeLastBell, removeLesson, sameDraft, setBell, setDays, setHoliday, setLesson, toRow, validate, type Draft,
} from '../../src/features/timetable';

const draft = (over: Partial<Draft> = {}): Draft => ({ ...blankDraft(), ...over });

describe('час', () => {
  it('нормалізує як Android: без провідного нуля', () => {
    expect(normTime('08:30')).toBe('8:30');
    expect(normTime(' 14:05 ')).toBe('14:05');
    expect(normTime('24:00')).toBeNull();
    expect(normTime('8:3')).toBeNull();
    expect(normTime('')).toBeNull();
  });
  it('для <input type=time> — з нулем', () => expect(inputTime('8:30')).toBe('08:30'));
});

describe('перевірка — ті самі правила, що в Android і на сервері', () => {
  it('порожній розклад з типовими дзвінками валідний', () => expect(validate(blankDraft())).toEqual([]));
  it('дзвінки: немає, забагато, перевернуті, накладаються, порожні', () => {
    expect(validate(draft({ bells: [] }))).toContain('Додай хоча б один дзвінок');
    const many = draft({ bells: Array.from({ length: 13 }, (_, i) => ({ start: `${8 + (i % 10)}:00`, end: `${8 + (i % 10)}:45` })) });
    expect(validate(many)).toContain(`Не більше ${MAX_LESSONS} уроків на день`);
    expect(validate(draft({ bells: [{ start: '9:00', end: '8:45' }] }))).toContain('1 урок: кінець має бути пізніше за початок');
    expect(validate(draft({ bells: [{ start: '8:30', end: '9:15' }, { start: '9:10', end: '9:55' }] })))
      .toContain('2 урок починається раніше, ніж закінчується 1 урок');
    expect(validate(draft({ bells: [{ start: '', end: '9:15' }] }))).toContain('1 урок: вкажи час початку й кінця');
  });
  it('дні: 5–7, уроків не більше, ніж дзвінків, назви не порожні', () => {
    expect(validate(draft({ week: [[], [], [], []] }))).toContain('У тижні має бути від 5 до 7 днів');
    const tooMany = draft({ bells: [{ start: '8:30', end: '9:15' }], week: [[{ s: 'А', o: false }, { s: 'Б', o: false }], [], [], [], []] });
    expect(validate(tooMany)).toContain('Понеділок: уроків більше, ніж дзвінків (1)');
    expect(validate(draft({ week: [[], [{ s: '  ', o: false }], [], [], []] }))).toContain('Вівторок: порожня або задовга назва предмета');
    expect(validate(draft({ week: [[], [], [{ s: 'x'.repeat(61), o: false }], [], []] }))).toContain('Середа: порожня або задовга назва предмета');
  });
  it('канікули: дати й порядок', () => {
    expect(validate(draft({ holidays: [['2026-12-24', '']] }))).toContain('Канікули: вкажи обидві дати');
    expect(validate(draft({ holidays: [['2027-01-10', '2026-12-24']] }))).toContain('Канікули: кінець раніше за початок');
    expect(validate(draft({ holidays: [['2026-12-24', '2027-01-10']] }))).toEqual([]);
  });
});

describe('операції не змінюють оригінал', () => {
  it('уроки: додати, змінити, переставити, прибрати', () => {
    const a = blankDraft();
    const b = addLesson(addLesson(a, 0, 'Математика'), 0, 'Фізика');
    expect(a.week[0]).toEqual([]);
    expect(b.week[0]!.map((l) => l.s)).toEqual(['Математика', 'Фізика']);
    expect(moveLesson(b, 0, 1, -1).week[0]!.map((l) => l.s)).toEqual(['Фізика', 'Математика']);
    expect(moveLesson(b, 0, 0, -1)).toEqual(b);
    expect(setLesson(b, 0, 1, { o: true }).week[0]![1]).toEqual({ s: 'Фізика', o: true });
    expect(removeLesson(b, 0, 0).week[0]!.map((l) => l.s)).toEqual(['Фізика']);
  });
  it('урок не додається, якщо немає вільного дзвінка', () => {
    const one = draft({ bells: [{ start: '8:30', end: '9:15' }] });
    const full = addLesson(one, 0, 'А');
    expect(addLesson(full, 0, 'Б')).toBe(full);
  });
  it('дні: 5–7, нові порожні, зайві прибираються', () => {
    const a = addLesson(blankDraft(), 4, 'Музика');
    expect(setDays(a, 6).week).toHaveLength(6);
    expect(setDays(a, 6).week[4]![0]!.s).toBe('Музика');
    expect(setDays(a, 2).week).toHaveLength(5);
    expect(setDays(a, 9).week).toHaveLength(7);
  });
  it('дзвінки: новий — через 10 хв після останнього на 45 хв; максимум 12', () => {
    const b = addBell(blankDraft());
    expect(b.bells.at(-1)).toEqual({ start: '15:25', end: '16:10' });
    let d = blankDraft();
    for (let i = 0; i < 10; i++) d = addBell(d);
    expect(d.bells).toHaveLength(MAX_LESSONS);
    expect(setBell(d, 0, { start: '8:00' }).bells[0]).toEqual({ start: '8:00', end: '9:15' });
  });
  it('прибрати останній дзвінок — зникають і уроки, яким він був потрібен', () => {
    let d = draft({ bells: [{ start: '8:30', end: '9:15' }, { start: '9:25', end: '10:10' }] });
    d = addLesson(addLesson(d, 0, 'А'), 0, 'Б');
    d = addLesson(d, 1, 'В');
    expect(lessonsLostByLastBell(d)).toBe(1);
    const r = removeLastBell(d);
    expect(r.bells).toHaveLength(1);
    expect(r.week[0]!.map((l) => l.s)).toEqual(['А']);
    expect(r.week[1]!.map((l) => l.s)).toEqual(['В']);
  });
  it('канікули', () => {
    const d = addHoliday(blankDraft(), '2026-12-24', '2027-01-10');
    expect(setHoliday(d, 0, '2026-12-25', '2027-01-10').holidays).toEqual([['2026-12-25', '2027-01-10']]);
  });
});

describe('рядок school_timetables', () => {
  it('туди й назад без втрат; час і факультатив — як пише Android', () => {
    let d = setBell(blankDraft(), 0, { start: '08:30' });
    d = setLesson(addLesson(d, 0, ' Математика '), 0, 0, { o: true });
    d = addLesson(d, 1, 'Фізика');
    const row = toRow(d, 'kid', 'fam');
    expect(row.bells[0]).toEqual(['8:30', '9:15']);
    expect(row.week[0]).toEqual([{ s: 'Математика', o: true }]);
    expect(row.week[1]).toEqual([{ s: 'Фізика' }]);
    expect(row.family_id).toBe('fam');
    expect(sameDraft(fromRow(row), fromRow(toRow(fromRow(row), 'kid', 'fam')))).toBe(true);
  });
  it('без розкладу — порожній з типовими дзвінками', () => {
    expect(fromRow(null)).toEqual(blankDraft());
    expect(fromRow(null).bells).toHaveLength(7);
  });
});
