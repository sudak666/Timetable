/**
 * Редактор розкладу: чернетка, незмінні операції над нею й перевірка.
 * Чиста логіка без DOM — тестується у vitest. Правила ті самі, що в Android (ScheduleValidator)
 * і на сервері (school_timetable_valid у supabase/migrations/20261108_school_timetables.sql).
 */
import { DAY_NAMES, type TimetableRow } from '../data/schedule';

export interface Bell { start: string; end: string }
export interface DraftLesson { s: string; o: boolean }
export interface Draft { bells: Bell[]; week: DraftLesson[][]; holidays: [string, string][] }

export const MAX_LESSONS = 12;
export const MIN_DAYS = 5;
export const MAX_DAYS = 7;
export const MAX_HOLIDAYS = 30;
export const MAX_SUBJECT = 60;

/** Типові дзвінки (45 хв, великі перерви після 2-го й 4-го) — як TYPICAL_BELLS в Android. */
export const TYPICAL_BELLS: Bell[] = [
  ['8:30', '9:15'], ['9:25', '10:10'], ['10:30', '11:15'], ['11:25', '12:10'], ['12:30', '13:15'], ['13:35', '14:20'], ['14:30', '15:15'],
].map(([start, end]) => ({ start: start!, end: end! }));

const TIME = /^([01]?\d|2[0-3]):([0-5]\d)$/;

/** «08:30» (з <input type=time>) → «8:30», як пише Android і очікує сервер. null — не час. */
export function normTime(t: string): string | null {
  const m = TIME.exec(t.trim());
  return m ? `${Number(m[1])}:${m[2]}` : null;
}

/** «8:30» → «08:30» для <input type=time>. */
export const inputTime = (t: string): string => (normTime(t) ?? '').padStart(5, '0');

const minutes = (t: string): number => {
  const [h = '0', m = '0'] = t.split(':');
  return Number(h) * 60 + Number(m);
};

export const blankDraft = (): Draft => ({ bells: TYPICAL_BELLS.map((b) => ({ ...b })), week: Array.from({ length: MIN_DAYS }, () => []), holidays: [] });

export function fromRow(row: TimetableRow | null | undefined): Draft {
  if (!row) return blankDraft();
  return {
    bells: row.bells.map(([start, end]) => ({ start, end })),
    week: row.week.map((d) => d.map((l) => ({ s: l.s, o: !!l.o }))),
    holidays: row.holidays.map(([a, b]) => [a, b]),
  };
}

/** Формат рядка school_timetables. Факультатив пишемо лише як o: true — так само, як Android. */
export function toRow(d: Draft, childId: string, familyId: string): TimetableRow & { family_id: string } {
  return {
    child_id: childId,
    family_id: familyId,
    bells: d.bells.map((b) => [normTime(b.start) ?? b.start, normTime(b.end) ?? b.end]),
    week: d.week.map((day) => day.map((l) => (l.o ? { s: l.s.trim(), o: true } : { s: l.s.trim() }))),
    holidays: d.holidays.map(([a, b]) => [a, b]),
  };
}

const dayName = (i: number) => DAY_NAMES[i] ?? `День ${i + 1}`;

/** Помилки людською мовою; порожній список — можна зберігати. */
export function validate(d: Draft): string[] {
  const e: string[] = [];
  if (!d.bells.length) e.push('Додай хоча б один дзвінок');
  if (d.bells.length > MAX_LESSONS) e.push(`Не більше ${MAX_LESSONS} уроків на день`);
  d.bells.forEach((b, i) => {
    const s = normTime(b.start), f = normTime(b.end);
    if (!s || !f) return void e.push(`${i + 1} урок: вкажи час початку й кінця`);
    if (minutes(f) <= minutes(s)) e.push(`${i + 1} урок: кінець має бути пізніше за початок`);
    const prev = d.bells[i - 1], pe = prev && normTime(prev.end);
    if (pe && minutes(s) < minutes(pe)) e.push(`${i + 1} урок починається раніше, ніж закінчується ${i} урок`);
  });
  if (d.week.length < MIN_DAYS || d.week.length > MAX_DAYS) e.push(`У тижні має бути від ${MIN_DAYS} до ${MAX_DAYS} днів`);
  d.week.forEach((day, i) => {
    if (day.length > d.bells.length) e.push(`${dayName(i)}: уроків більше, ніж дзвінків (${d.bells.length})`);
    if (day.some((l) => !l.s.trim() || l.s.trim().length > MAX_SUBJECT)) e.push(`${dayName(i)}: порожня або задовга назва предмета`);
  });
  if (d.holidays.length > MAX_HOLIDAYS) e.push(`Забагато канікул (максимум ${MAX_HOLIDAYS})`);
  if (d.holidays.some(([a, b]) => !/^\d{4}-\d{2}-\d{2}$/.test(a) || !/^\d{4}-\d{2}-\d{2}$/.test(b))) e.push('Канікули: вкажи обидві дати');
  else if (d.holidays.some(([a, b]) => b < a)) e.push('Канікули: кінець раніше за початок');
  return e;
}

/* ---------- незмінні операції: повертають нову чернетку ---------- */

const withDay = (d: Draft, day: number, f: (l: DraftLesson[]) => DraftLesson[]): Draft =>
  ({ ...d, week: d.week.map((ls, i) => (i === day ? f(ls) : ls)) });

/** Новий урок наприкінці дня — якщо є вільний дзвінок. */
export const addLesson = (d: Draft, day: number, s: string): Draft =>
  ((d.week[day]?.length ?? MAX_LESSONS) >= d.bells.length ? d : withDay(d, day, (ls) => [...ls, { s, o: false }]));

export const setLesson = (d: Draft, day: number, i: number, patch: Partial<DraftLesson>): Draft =>
  withDay(d, day, (ls) => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)));

export const removeLesson = (d: Draft, day: number, i: number): Draft => withDay(d, day, (ls) => ls.filter((_, j) => j !== i));

/** Переставити урок на dir (−1 вгору, +1 вниз) у межах дня. */
export function moveLesson(d: Draft, day: number, i: number, dir: -1 | 1): Draft {
  const j = i + dir;
  return withDay(d, day, (ls) => {
    if (j < 0 || j >= ls.length) return ls;
    const c = [...ls];
    [c[i], c[j]] = [c[j]!, c[i]!];
    return c;
  });
}

/** Кількість навчальних днів (5–7). Зайві дні прибираються, нові — порожні. */
export function setDays(d: Draft, n: number): Draft {
  const k = Math.min(MAX_DAYS, Math.max(MIN_DAYS, n));
  return { ...d, week: Array.from({ length: k }, (_, i) => d.week[i] ?? []) };
}

/** Новий дзвінок: через 10 хв після останнього, на 45 хв (типовий урок). */
export function addBell(d: Draft): Draft {
  if (d.bells.length >= MAX_LESSONS) return d;
  const last = d.bells.at(-1), from = last && normTime(last.end) ? minutes(last.end) + 10 : 8 * 60 + 30;
  const t = (m: number) => `${Math.floor(m / 60) % 24}:${String(m % 60).padStart(2, '0')}`;
  return { ...d, bells: [...d.bells, { start: t(from), end: t(from + 45) }] };
}

export const setBell = (d: Draft, i: number, patch: Partial<Bell>): Draft =>
  ({ ...d, bells: d.bells.map((b, j) => (j === i ? { ...b, ...patch } : b)) });

/** Прибрати останній дзвінок; уроки, яким він був потрібен, теж зникають (інакше розклад неможливий). */
export function removeLastBell(d: Draft): Draft {
  if (!d.bells.length) return d;
  const n = d.bells.length - 1;
  return { ...d, bells: d.bells.slice(0, n), week: d.week.map((ls) => ls.slice(0, n)) };
}

/** Скільки уроків зникне, якщо прибрати останній дзвінок — щоб чесно попередити. */
export const lessonsLostByLastBell = (d: Draft): number => d.week.filter((ls) => ls.length >= d.bells.length && ls.length > 0).length;

export const addHoliday = (d: Draft, from: string, to: string): Draft =>
  (d.holidays.length >= MAX_HOLIDAYS ? d : { ...d, holidays: [...d.holidays, [from, to]] });

export const setHoliday = (d: Draft, i: number, from: string, to: string): Draft =>
  ({ ...d, holidays: d.holidays.map((h, j) => (j === i ? [from, to] : h)) });

export const removeHoliday = (d: Draft, i: number): Draft => ({ ...d, holidays: d.holidays.filter((_, j) => j !== i) });

/** Чи є незбережені зміни (порівняння за змістом, а не за посиланням). */
export const sameDraft = (a: Draft, b: Draft): boolean => JSON.stringify(a) === JSON.stringify(b);
