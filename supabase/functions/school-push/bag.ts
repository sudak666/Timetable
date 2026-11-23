/**
 * «Збери рюкзак»: що взяти на завтра — уроки й речі на заняття. Ті самі правила, що в картці «Рюкзак»
 * на сайті й в Android: уроки — за днем тижня, крім канікул; заняття — і на канікулах (src/lib/clubs.ts).
 * Чиста логіка без мережі — тести у vitest (tests/unit/bag.test.ts).
 */

export interface TimetableJson { child_id: string; week: unknown; holidays: unknown; activities: unknown }
export interface Thing { e: string; t: string; n: string }
export interface Bag { subjects: string[]; things: Thing[] }

/** День тижня 1..7 (1 = понеділок) для дати YYYY-MM-DD. */
export const isoWeekday = (date: string): number => ((new Date(date + "T12:00:00Z").getUTCDay() + 6) % 7) + 1;

const isArr = (x: unknown): x is unknown[] => Array.isArray(x);
const str = (x: unknown): string => (typeof x === "string" ? x.trim() : "");
/** «9:05» → 545 (як toMin у src/lib/clubs.ts): рядки «16:00» < «9:00» сортувались би неправильно. */
const minutes = (t: unknown): number => {
  const [h = "0", m = "0"] = str(t).split(":");
  return Number(h) * 60 + Number(m);
};

/** Що покласти в рюкзак на дату (за розкладом дитини). Кривий JSON — просто порожньо, без падіння. */
export function bagFor(row: TimetableJson, date: string): Bag {
  const wd = isoWeekday(date);
  const holiday = isArr(row.holidays) && row.holidays.some((h) => isArr(h) && str(h[0]) <= date && date <= str(h[1]));
  const day = !holiday && isArr(row.week) ? row.week[wd - 1] : null;
  const subjects = isArr(day)
    ? [...new Set(day.map((l) => (l && typeof l === "object" ? str((l as { s?: unknown }).s) : "")).filter(Boolean))]
    : [];
  const things = (isArr(row.activities) ? row.activities : [])
    .map((a) => (a && typeof a === "object" ? (a as Record<string, unknown>) : {}))
    .filter((a) => isArr(a.d) && a.d.includes(wd) && str(a.n))
    .sort((x, y) => minutes(x.a) - minutes(y.a))
    .map((a) => ({ e: str(a.e), t: str(a.t), n: str(a.n) }));
  return { subjects, things };
}

/** 1–4, 21–24… — «завдання»; 5–20, 25–30… — «завдань». */
export const tasks = (n: number): string => (n % 10 >= 1 && n % 10 <= 4 && !(n % 100 >= 11 && n % 100 <= 14) ? "завдання" : "завдань");

/**
 * Одне вечірнє сповіщення дитині: рюкзак (уроки, речі на заняття) і невиконана домашка на завтра.
 * Немає ні уроків, ні речей — лишається звичне нагадування про домашку; нічого немає — null.
 */
export function eveningMessage(bag: Bag, homework: string[]): { title: string; body: string; kind: "bag" | "hw" } | null {
  const hw = [...new Set(homework)];
  if (bag.subjects.length || bag.things.length) {
    const lines = [
      bag.subjects.join(", "),
      ...bag.things.map((x) => `${x.e ? x.e + " " : ""}${x.t}: ${x.n}`),
      hw.length ? `📝 Домашка: ${hw.join(", ")}` : "",
    ].filter(Boolean);
    return { title: "🎒 Збери рюкзак на завтра", body: lines.join("\n"), kind: "bag" };
  }
  if (!homework.length) return null;
  return { title: `📝 На завтра ${homework.length} ${tasks(homework.length)}`, body: hw.join(", "), kind: "hw" };
}
