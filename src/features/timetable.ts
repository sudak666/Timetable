/**
 * Редактор розкладу: чернетка, незмінні операції над нею й перевірка.
 * Чиста логіка без DOM — тестується у vitest. Правила ті самі, що в Android (ScheduleValidator)
 * і на сервері (school_timetable_valid у supabase/migrations/20261108_school_timetables.sql).
 */
import { DAY_NAMES, type ActivityRow, type TimetableRow } from '../data/schedule';

export interface Bell { start: string; end: string }
export interface DraftLesson { s: string; o: boolean }
/** Позашкільне заняття в чернетці: p і n — порожній рядок, якщо не вказано. */
export interface DraftClub { t: string; e: string; d: number[]; a: string; b: string; p: string; n: string }
export interface Draft { bells: Bell[]; week: DraftLesson[][]; holidays: [string, string][]; clubs: DraftClub[] }

export const MAX_LESSONS = 12;
export const MIN_DAYS = 5;
export const MAX_DAYS = 7;
export const MAX_HOLIDAYS = 30;
export const MAX_SUBJECT = 60;
/** Ліміти занять — як school_activities_valid() на сервері й Club в Android. */
export const CLUB = { max: 20, title: 60, emoji: 16, place: 80, bring: 120 } as const;

/** Найпоширеніші заняття — для швидкого додавання (як Clubs.KINDS в Android). */
export const CLUB_KINDS: readonly (readonly [string, string])[] = [
  ['⚽', 'Футбол'], ['🏀', 'Баскетбол'], ['🏐', 'Волейбол'], ['🏊', 'Плавання'], ['🥋', 'Бойові мистецтва'], ['🤸', 'Гімнастика'],
  ['🎾', 'Теніс'], ['🏋️', 'Тренування'], ['💻', 'Програмування'], ['🤖', 'Робототехніка'], ['♟️', 'Шахи'], ['🎨', 'Малювання'],
  ['🎵', 'Музика'], ['💃', 'Танці'], ['🇬🇧', 'Англійська'], ['📚', 'Репетитор'],
];

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

export const blankDraft = (): Draft => ({ bells: TYPICAL_BELLS.map((b) => ({ ...b })), week: Array.from({ length: MIN_DAYS }, () => []), holidays: [], clubs: [] });

export function fromRow(row: TimetableRow | null | undefined): Draft {
  if (!row) return blankDraft();
  return {
    bells: row.bells.map(([start, end]) => ({ start, end })),
    week: row.week.map((d) => d.map((l) => ({ s: l.s, o: !!l.o }))),
    holidays: row.holidays.map(([a, b]) => [a, b]),
    clubs: (row.activities ?? []).map((c) => ({ t: c.t, e: c.e, d: [...c.d], a: c.a, b: c.b, p: c.p ?? '', n: c.n ?? '' })),
  };
}

/** Заняття для сервера: дні по порядку, час без провідного нуля, порожні поля — null (як Android). */
const clubRow = (c: DraftClub): ActivityRow => ({
  t: c.t.trim(), e: c.e.trim(), d: [...new Set(c.d)].sort((x, y) => x - y),
  a: normTime(c.a) ?? c.a, b: normTime(c.b) ?? c.b, p: c.p.trim() || null, n: c.n.trim() || null,
});

/** Формат рядка school_timetables. Факультатив пишемо лише як o: true — так само, як Android. */
export function toRow(d: Draft, childId: string, familyId: string): TimetableRow & { family_id: string } {
  return {
    child_id: childId,
    family_id: familyId,
    bells: d.bells.map((b) => [normTime(b.start) ?? b.start, normTime(b.end) ?? b.end]),
    week: d.week.map((day) => day.map((l) => (l.o ? { s: l.s.trim(), o: true } : { s: l.s.trim() }))),
    holidays: d.holidays.map(([a, b]) => [a, b]),
    activities: d.clubs.map(clubRow),
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
  if (d.clubs.length > CLUB.max) e.push(`Не більше ${CLUB.max} занять`);
  d.clubs.forEach((c, i) => {
    const name = c.t.trim() || `Заняття ${i + 1}`, s = normTime(c.a), f = normTime(c.b);
    if (!c.t.trim() || c.t.trim().length > CLUB.title) e.push(`${name}: порожня або задовга назва`);
    if (!c.e.trim() || c.e.trim().length > CLUB.emoji) e.push(`${name}: обери емодзі`);
    if (!c.d.length) e.push(`${name}: обери хоча б один день`);
    if (!s || !f) e.push(`${name}: вкажи час початку й кінця`);
    else if (minutes(f) <= minutes(s)) e.push(`${name}: кінець має бути пізніше за початок`);
    if (c.p.trim().length > CLUB.place) e.push(`${name}: задовга назва місця (максимум ${CLUB.place})`);
    if (c.n.trim().length > CLUB.bring) e.push(`${name}: задовгий список речей (максимум ${CLUB.bring})`);
  });
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

/* ---------- заняття ---------- */

/** Нове заняття (з шаблону або порожнє) — о 16:00 на 1,5 год, день обирають далі. */
export const addClub = (d: Draft, e = '⭐', t = ''): Draft =>
  (d.clubs.length >= CLUB.max ? d : { ...d, clubs: [...d.clubs, { t, e, d: [], a: '16:00', b: '17:30', p: '', n: '' }] });

export const setClub = (d: Draft, i: number, patch: Partial<DraftClub>): Draft =>
  ({ ...d, clubs: d.clubs.map((c, j) => (j === i ? { ...c, ...patch } : c)) });

/** Увімкнути/вимкнути день тижня (1..7) для заняття. */
export const toggleClubDay = (d: Draft, i: number, day: number): Draft => {
  const c = d.clubs[i];
  if (!c) return d;
  return setClub(d, i, { d: c.d.includes(day) ? c.d.filter((x) => x !== day) : [...c.d, day].sort((x, y) => x - y) });
};

export const removeClub = (d: Draft, i: number): Draft => ({ ...d, clubs: d.clubs.filter((_, j) => j !== i) });

/** Чи є незбережені зміни (порівняння за змістом, а не за посиланням). */
export const sameDraft = (a: Draft, b: Draft): boolean => JSON.stringify(a) === JSON.stringify(b);
