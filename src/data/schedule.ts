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
  /** Позашкільні заняття (див. supabase/migrations/20261114_school_activities.sql). Старі знімки — без поля. */
  activities?: ActivityRow[];
  /** Версія рядка (ставить тригер на сервері); є лише в рядках, прочитаних із сервера. */
  updated_at?: string;
}

/** Заняття: t — назва, e — емодзі, d — дні 1..7 (1 = понеділок), a/b — «H:MM», p — місце, n — що взяти. */
export interface ActivityRow { t: string; e: string; d: number[]; a: string; b: string; p?: string | null; n?: string | null }

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
/** Заняття вибраної дитини (у прикладі без входу — немає). */
export let ACTIVITIES: readonly ActivityRow[] = [];

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
  activities: [],
};

let applied: TimetableRow | null = null;

/** Підставляє розклад дитини (null — приклад за замовчуванням). Повертає true, якщо щось змінилося. */
export function applyTimetable(row: TimetableRow | null): boolean {
  if (row === applied) return false;
  applied = row;
  if (!row) {
    BELLS = DEFAULT_BELLS; WEEK = DEFAULT_WEEK; HOLIDAYS = DEFAULT_HOLIDAYS; ACTIVITIES = [];
  } else {
    BELLS = row.bells.map(([a, b]) => `${a}-${b}`);
    // Уроків не більше, ніж дзвінків (сервер це перевіряє, але захищаємось від старих даних).
    WEEK = row.week.map((d, i) => ({ name: DAY_NAMES[i] ?? `День ${i + 1}`, lessons: d.slice(0, BELLS.length).map((l) => (l.o ? { subject: l.s, optional: true } : { subject: l.s })) }));
    HOLIDAYS = row.holidays;
    ACTIVITIES = Array.isArray(row.activities) ? row.activities : [];
  }
  RANGES = rangesOf(BELLS);
  return true;
}

export const SUBJECTS = {
  'Математика': { color: '#1e56a0', emoji: '🧮' },
  'Алгебра': { color: '#2a4a96', emoji: '➗' },
  'Геометрія': { color: '#3b4290', emoji: '📐' },
  'Укр. мова': { color: '#b03a24', emoji: '✍️' },
  'Укр. література': { color: '#8f3a28', emoji: '📖' },
  'Література': { color: '#a1364f', emoji: '📚' },
  'Зарубіжна література': { color: '#88406a', emoji: '🌐' },
  'Англ. мова': { color: '#1b6a86', emoji: '🇬🇧' },
  'Нім. мова': { color: '#2d5a6b', emoji: '🇩🇪' },
  'Історія України': { color: '#8a6200', emoji: '🇺🇦' },
  'Всесв. історія': { color: '#a0561c', emoji: '🏛️' },
  'Громад. освіта': { color: '#76502e', emoji: '🤝' },
  'Я досліджую світ': { color: '#2e7a3b', emoji: '🔍' },
  'Природознавство': { color: '#3e6b22', emoji: '🍃' },
  'Довкілля': { color: '#2a7552', emoji: '🌱' },
  'Біологія': { color: '#2b7340', emoji: '🧬' },
  'Географія': { color: '#1d7066', emoji: '🌍' },
  'Фізика': { color: '#2e4777', emoji: '⚛️' },
  'Хімія': { color: '#7a3e78', emoji: '🧪' },
  'Астрономія': { color: '#343f6c', emoji: '🔭' },
  'Інформатика': { color: '#0e6a70', emoji: '💻' },
  'STEM': { color: '#1b735e', emoji: '🤖' },
  'Технології': { color: '#5c6626', emoji: '🛠️' },
  'Мистецтво': { color: '#ad5418', emoji: '🎨' },
  'Музика': { color: '#963a66', emoji: '🎵' },
  'Фіз-ра': { color: '#bc3326', emoji: '⚽' },
  'Здоров’я': { color: '#ae3f27', emoji: '❤️' },
  'ЗБД': { color: '#4b5563', emoji: '🦺' },
  'Захист України': { color: '#3e4a38', emoji: '🛡️' },
} as const satisfies Record<string, { color: string; emoji: string }>;

/** Предмети з розкладу дитини (без повторів). */
export const scheduleSubjects = (): string[] => [...new Set(WEEK.flatMap((d) => d.lessons.map((l) => l.subject)))];
/** Предмети для вибору: спершу ті, що є в розкладі дитини, далі решта довідника. */
export const subjectList = (): string[] => [...new Set([...scheduleSubjects(), ...Object.keys(SUBJECTS)])];
export const isSubject = (s: string): s is Subject => s in SUBJECTS;
export const subjEmoji = (s: string): string => (isSubject(s) ? SUBJECTS[s].emoji : '📘');
