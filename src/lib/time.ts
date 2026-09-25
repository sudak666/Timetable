import { RANGES, WEEK, type Lesson } from '../data/schedule';

export const DAY_MS = 864e5;

/** Локальна дата у форматі YYYY-MM-DD (без зсуву UTC). */
export const iso = (d: Date): string => new Date(d.getTime() - d.getTimezoneOffset() * 6e4).toISOString().slice(0, 10);
export const addDays = (d: Date, n: number): Date => new Date(d.getTime() + n * DAY_MS);
export const mondayOf = (d: Date): Date => {
  const m = new Date(d);
  m.setHours(12, 0, 0, 0);
  m.setDate(m.getDate() - ((m.getDay() + 6) % 7));
  return m;
};

export interface Clock { date: Date; weekday: number; min: number }
/** weekday: 0 = понеділок … 6 = неділя; min: хвилини від опівночі з дробовими секундами. */
export const clockOf = (date: Date): Clock => ({
  date,
  weekday: (date.getDay() + 6) % 7,
  min: date.getHours() * 60 + date.getMinutes() + date.getSeconds() / 60,
});

export type Status =
  | { kind: 'lesson'; idx: number; start: number; end: number; lessons: Lesson[] }
  | { kind: 'break'; idx: number; start: number; prevEnd: number; lessons: Lesson[] }
  | { kind: 'before'; idx: 0; start: number; lessons: Lesson[] }
  | { kind: 'done' };

export function statusAt(c: Clock): Status {
  const lessons = WEEK[c.weekday]?.lessons;
  if (!lessons) return { kind: 'done' };
  for (let i = 0; i < lessons.length; i++) {
    const [a, b] = RANGES[i]!;
    if (c.min >= a && c.min < b) return { kind: 'lesson', idx: i, start: a, end: b, lessons };
    if (c.min < a) return i === 0 ? { kind: 'before', idx: 0, start: a, lessons } : { kind: 'break', idx: i, start: a, prevEnd: RANGES[i - 1]![1], lessons };
  }
  return { kind: 'done' };
}

/** Наступний навчальний день після weekday (0..6). */
export const nextSchoolDay = (weekday: number): number => {
  for (let i = weekday + 1; i < 7; i++) if (WEEK[i]) return i;
  return 0;
};

/** Хвилини → «h:mm:ss» або «m:ss». */
export const hms = (minutes: number): string => {
  const s = Math.max(0, Math.ceil(minutes * 60));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  const p = (v: number) => String(v).padStart(2, '0');
  return (h ? `${h}:${p(m)}` : String(m)) + ':' + p(sec);
};

export const fmtDay = (isoDate: string): string =>
  new Date(isoDate + 'T12:00').toLocaleDateString('uk-UA', { weekday: 'short', day: 'numeric', month: 'short' });

/** Найближча дата (після сьогодні), коли є урок предмета. */
export function nextLessonDate(subject: string, from: Date): string {
  for (let i = 1; i <= 14; i++) {
    const d = addDays(from, i);
    if (WEEK[(d.getDay() + 6) % 7]?.lessons.some((l) => l.subject === subject)) return iso(d);
  }
  return iso(addDays(from, 1));
}
