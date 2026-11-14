/**
 * Позашкільні заняття на «Сьогодні»: що сьогодні/завтра і що взяти. Чиста логіка — тести у vitest.
 * Ті самі правила, що в Android (AfterSchool.kt): показуються й на канікулах.
 */
import type { ActivityRow } from '../data/schedule';

export const DAY_SHORT = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Нд'] as const;

export const toMin = (t: string): number => {
  const [h = '0', m = '0'] = t.split(':');
  return Number(h) * 60 + Number(m);
};

/** День тижня 1..7 (1 = понеділок), як у форматі сервера. */
export const isoDay = (d: Date): number => ((d.getDay() + 6) % 7) + 1;

/** Заняття на дату, за часом початку. */
export const clubsOn = (list: readonly ActivityRow[], d: Date): ActivityRow[] =>
  list.filter((c) => c.d.includes(isoDay(d))).sort((x, y) => toMin(x.a) - toMin(y.a));

export type ClubState = 'past' | 'cur' | '';
export interface AfterSchool { today: boolean; rows: { c: ActivityRow; st: ClubState }[] }

/** Сьогоднішні, поки не скінчилось останнє; потім — завтрашні; нічого — null. */
export function afterSchool(list: readonly ActivityRow[], now: Date): AfterSchool | null {
  const min = now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60;
  const today = clubsOn(list, now);
  if (today.some((c) => min < toMin(c.b))) {
    return { today: true, rows: today.map((c) => ({ c, st: min >= toMin(c.b) ? 'past' : min >= toMin(c.a) ? 'cur' : '' })) };
  }
  const tm = new Date(now);
  tm.setDate(tm.getDate() + 1);
  const next = clubsOn(list, tm);
  return next.length ? { today: false, rows: next.map((c) => ({ c, st: '' })) } : null;
}

/**
 * Що взяти: на сьогодні, поки уроки не скінчились (schoolDone = false), інакше на завтра.
 * day — короткий день, якщо він не збігається з днем шкільного рюкзака (bagWeekday 0..6).
 */
export function clubBag(list: readonly ActivityRow[], now: Date, schoolDone: boolean, bagWeekday: number): { c: ActivityRow; day: string | null }[] {
  const d = new Date(now);
  if (schoolDone) d.setDate(d.getDate() + 1);
  const min = now.getHours() * 60 + now.getMinutes();
  const wd = isoDay(d) - 1;
  return clubsOn(list, d)
    .filter((c) => c.n?.trim() && (schoolDone || min < toMin(c.b)))
    .map((c) => ({ c, day: wd === bagWeekday ? null : DAY_SHORT[wd]! }));
}

/** «Пн, Ср»; будні — «Пн–Пт»; увесь тиждень — «щодня». */
export function daysText(d: readonly number[]): string {
  const s = [...new Set(d)].sort((a, b) => a - b);
  if (s.length === 7) return 'щодня';
  if (s.join() === '1,2,3,4,5') return 'Пн–Пт';
  return s.map((x) => DAY_SHORT[x - 1] ?? '').join(', ');
}

/** Найближче заняття, про яке пора нагадати: початок через (0, lead] хв від now. */
export function dueReminder(list: readonly ActivityRow[], now: Date, lead: number): ActivityRow | null {
  const min = now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60;
  return clubsOn(list, now).find((c) => toMin(c.a) - min > 0 && toMin(c.a) - min <= lead) ?? null;
}
