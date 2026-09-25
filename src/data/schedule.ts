export type Subject =
  | 'Математика' | 'Всесв. історія' | 'Література' | 'Англ. мова' | 'Укр. мова' | 'STEM' | 'Інформатика'
  | 'Географія' | 'Фіз-ра' | 'Технології' | 'Громад. освіта' | 'Довкілля' | 'Мистецтво' | 'ЗБД';

export interface Lesson { subject: Subject; optional?: boolean }
export interface Day { name: string; lessons: Lesson[] }

export const BELLS = ['8:30-9:15', '9:25-10:10', '10:30-11:15', '11:25-12:10', '12:30-13:15', '13:35-14:20', '14:30-15:15'] as const;

const toMin = (s: string): number => {
  const [h = '0', m = '0'] = s.split(':');
  return Number(h) * 60 + Number(m);
};
/** [початок, кінець] кожного уроку у хвилинах від опівночі */
export const RANGES: readonly (readonly [number, number])[] = BELLS.map((b) => {
  const [a = '', e = ''] = b.split('-');
  return [toMin(a), toMin(e)] as const;
});
export const bellStart = (i: number): string => BELLS[i]!.split('-')[0]!;
export const bellEnd = (i: number): string => BELLS[i]!.split('-')[1]!;

const L = (...s: Subject[]): Lesson[] => s.map((subject) => ({ subject }));

export const WEEK: Day[] = [
  { name: 'Понеділок', lessons: [...L('Математика', 'Всесв. історія', 'Література', 'Англ. мова', 'Укр. мова', 'STEM'), { subject: 'Математика', optional: true }] },
  { name: 'Вівторок', lessons: L('Література', 'Інформатика', 'Укр. мова', 'Географія', 'Математика', 'Фіз-ра') },
  { name: 'Середа', lessons: L('Англ. мова', 'Математика', 'Технології', 'Укр. мова', 'Громад. освіта', 'Довкілля', 'STEM') },
  { name: 'Четвер', lessons: L('Математика', 'Укр. мова', 'Географія', 'Фіз-ра', 'Англ. мова', 'Мистецтво', 'ЗБД') },
  { name: 'П’ятниця', lessons: L('Всесв. історія', 'Англ. мова', 'Література', 'Фіз-ра', 'Довкілля', 'Математика') },
];

export const SUBJECTS: Record<Subject, { color: string; emoji: string }> = {
  'Математика': { color: '#6c5ce7', emoji: '🧮' },
  'Всесв. історія': { color: '#b84a00', emoji: '🏛️' },
  'Література': { color: '#c2185b', emoji: '📚' },
  'Англ. мова': { color: '#0869b5', emoji: '🇬🇧' },
  'Укр. мова': { color: '#8a5c00', emoji: '✍️' },
  'STEM': { color: '#00866b', emoji: '🤖' },
  'Інформатика': { color: '#00797a', emoji: '💻' },
  'Географія': { color: '#12806a', emoji: '🌍' },
  'Фіз-ра': { color: '#c62828', emoji: '⚽' },
  'Технології': { color: '#6d5bd0', emoji: '🛠️' },
  'Громад. освіта': { color: '#ad1f76', emoji: '🤝' },
  'Довкілля': { color: '#1e7d43', emoji: '🌱' },
  'Мистецтво': { color: '#b35c00', emoji: '🎨' },
  'ЗБД': { color: '#4b5563', emoji: '🦺' },
};
export const SUBJECT_LIST = Object.keys(SUBJECTS) as Subject[];
export const isSubject = (s: string): s is Subject => s in SUBJECTS;
export const subjEmoji = (s: string): string => (isSubject(s) ? SUBJECTS[s].emoji : '📘');
