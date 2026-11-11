/**
 * Розклад, дзвінки й канікули. Це «живі» прив'язки (export let): після входу їх замінює
 * розклад вибраної дитини з таблиці school_timetables (applyTimetable). Без входу — приклад нижче.
 */
export type Subject = keyof typeof SUBJECTS;

export interface Lesson { subject: string; optional?: boolean }
export interface Day { name: string; lessons: Lesson[] }

/** Рядок school_timetables — формат спільний із Android (див. supabase/migrations/20261108_school_timetables.sql). */
export interface TimetableRow {
  child_id: string;
  bells: [string, string][];
  week: { s: string; o?: boolean }[][];
  holidays: [string, string][];
}

export const DAY_NAMES = ['Понеділок', 'Вівторок', 'Середа', 'Четвер', 'П’ятниця', 'Субота', 'Неділя'] as const;

const toMin = (s: string): number => {
  const [h = '0', m = '0'] = s.split(':');
  return Number(h) * 60 + Number(m);
};

const DEFAULT_BELLS = ['8:30-9:15', '9:25-10:10', '10:30-11:15', '11:25-12:10', '12:30-13:15', '13:35-14:20', '14:30-15:15'];

const L = (...s: string[]): Lesson[] => s.map((subject) => ({ subject }));

const DEFAULT_WEEK: Day[] = [
  { name: 'Понеділок', lessons: [...L('Математика', 'Всесв. історія', 'Література', 'Англ. мова', 'Укр. мова', 'STEM'), { subject: 'Математика', optional: true }] },
  { name: 'Вівторок', lessons: L('Література', 'Інформатика', 'Укр. мова', 'Географія', 'Математика', 'Фіз-ра') },
  { name: 'Середа', lessons: L('Англ. мова', 'Математика', 'Технології', 'Укр. мова', 'Громад. освіта', 'Довкілля', 'STEM') },
  { name: 'Четвер', lessons: L('Математика', 'Укр. мова', 'Географія', 'Фіз-ра', 'Англ. мова', 'Мистецтво', 'ЗБД') },
  { name: 'П’ятниця', lessons: L('Всесв. історія', 'Англ. мова', 'Література', 'Фіз-ра', 'Довкілля', 'Математика') },
];

/** Канікули: [перший, останній] день включно, YYYY-MM-DD. Київ 2026/2027 (рекомендації ДОН КМДА). */
const DEFAULT_HOLIDAYS: (readonly [string, string])[] = [
  ['2026-10-26', '2026-11-01'],
  ['2026-12-24', '2027-01-10'],
  ['2027-03-22', '2027-03-28'],
  ['2027-05-29', '2027-08-31'],
];

const rangesOf = (bells: readonly string[]) => bells.map((b) => {
  const [a = '', e = ''] = b.split('-');
  return [toMin(a), toMin(e)] as const;
});

export let BELLS: readonly string[] = DEFAULT_BELLS;
/** [початок, кінець] кожного уроку у хвилинах від опівночі */
export let RANGES: readonly (readonly [number, number])[] = rangesOf(DEFAULT_BELLS);
export let WEEK: readonly Day[] = DEFAULT_WEEK;
export let HOLIDAYS: readonly (readonly [string, string])[] = DEFAULT_HOLIDAYS;

export const bellStart = (i: number): string => BELLS[i]?.split('-')[0] ?? '';
export const bellEnd = (i: number): string => BELLS[i]?.split('-')[1] ?? '';

/**
 * Порожній розклад для того, хто увійшов, але розкладу ще не має: не показуємо чужий приклад
 * (і не шлемо нагадувань про вигадані уроки), а пропонуємо заповнити свій — як в Android.
 */
export const EMPTY_TIMETABLE: TimetableRow = {
  child_id: '',
  bells: DEFAULT_BELLS.map((b) => b.split('-') as [string, string]),
  week: [[], [], [], [], []],
  holidays: [],
};

let applied: TimetableRow | null = null;

/** Підставляє розклад дитини (null — приклад за замовчуванням). Повертає true, якщо щось змінилося. */
export function applyTimetable(row: TimetableRow | null): boolean {
  if (row === applied) return false;
  applied = row;
  if (!row) {
    BELLS = DEFAULT_BELLS; WEEK = DEFAULT_WEEK; HOLIDAYS = DEFAULT_HOLIDAYS;
  } else {
    BELLS = row.bells.map(([a, b]) => `${a}-${b}`);
    // Уроків не більше, ніж дзвінків (сервер це перевіряє, але захищаємось від старих даних).
    WEEK = row.week.map((d, i) => ({ name: DAY_NAMES[i] ?? `День ${i + 1}`, lessons: d.slice(0, BELLS.length).map((l) => (l.o ? { subject: l.s, optional: true } : { subject: l.s })) }));
    HOLIDAYS = row.holidays;
  }
  RANGES = rangesOf(BELLS);
  return true;
}

export const SUBJECTS = {
  'Математика': { color: '#6c5ce7', emoji: '🧮' },
  'Алгебра': { color: '#5b4bd6', emoji: '➗' },
  'Геометрія': { color: '#4a3ac4', emoji: '📐' },
  'Укр. мова': { color: '#8a5c00', emoji: '✍️' },
  'Укр. література': { color: '#a0522d', emoji: '📖' },
  'Література': { color: '#c2185b', emoji: '📚' },
  'Зарубіжна література': { color: '#ad1457', emoji: '🌐' },
  'Англ. мова': { color: '#0869b5', emoji: '🇬🇧' },
  'Нім. мова': { color: '#1a5276', emoji: '🇩🇪' },
  'Історія України': { color: '#1f5fad', emoji: '🇺🇦' },
  'Всесв. історія': { color: '#b84a00', emoji: '🏛️' },
  'Громад. освіта': { color: '#ad1f76', emoji: '🤝' },
  'Я досліджую світ': { color: '#2e7d32', emoji: '🔍' },
  'Природознавство': { color: '#33691e', emoji: '🍃' },
  'Довкілля': { color: '#1e7d43', emoji: '🌱' },
  'Біологія': { color: '#2e7d32', emoji: '🧬' },
  'Географія': { color: '#12806a', emoji: '🌍' },
  'Фізика': { color: '#283593', emoji: '⚛️' },
  'Хімія': { color: '#6a1b9a', emoji: '🧪' },
  'Астрономія': { color: '#303f9f', emoji: '🔭' },
  'Інформатика': { color: '#00797a', emoji: '💻' },
  'STEM': { color: '#00866b', emoji: '🤖' },
  'Технології': { color: '#6d5bd0', emoji: '🛠️' },
  'Мистецтво': { color: '#b35c00', emoji: '🎨' },
  'Музика': { color: '#c2185b', emoji: '🎵' },
  'Фіз-ра': { color: '#c62828', emoji: '⚽' },
  'Здоров’я': { color: '#bf360c', emoji: '❤️' },
  'ЗБД': { color: '#4b5563', emoji: '🦺' },
  'Захист України': { color: '#37474f', emoji: '🛡️' },
} as const satisfies Record<string, { color: string; emoji: string }>;

/** Предмети з розкладу дитини (без повторів). */
export const scheduleSubjects = (): string[] => [...new Set(WEEK.flatMap((d) => d.lessons.map((l) => l.subject)))];
/** Предмети для вибору: спершу ті, що є в розкладі дитини, далі решта довідника. */
export const subjectList = (): string[] => [...new Set([...scheduleSubjects(), ...Object.keys(SUBJECTS)])];
export const isSubject = (s: string): s is Subject => s in SUBJECTS;
export const subjEmoji = (s: string): string => (isSubject(s) ? SUBJECTS[s].emoji : '📘');
