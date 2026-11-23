/**
 * Повітряні тривоги: чиста логіка (без мережі й DOM) — так само, як в Android
 * (core/ui/AlertBanner.kt, feature/profile/alerts/RegionItems.kt), щоб сайт і застосунок поводились однаково.
 * Дані — alerts.in.ua через наш сервер (school_alert_state оновлює cron щохвилини).
 */

/** Код сервера: A — тривога, P — у частині області/району, N — немає. */
export type AlertCode = 'A' | 'P' | 'N';
export type AlertKind = 'oblast' | 'city' | 'raion' | 'hromada';

export interface AlertLoc { uid: number; name: string; kind: AlertKind; oblast_uid: number | null; raion_uid: number | null }

/** Місце, яке обрала сім'я, і його стан. status === null — стан ще не відомий (сервер перевірить протягом хвилини). */
export interface AirAlert { uid: number; name: string; kind: AlertKind; status: AlertCode | null; since: string | null; checkedAt: string | null }

export interface AlertBanner { active: boolean; title: string; detail: string }

/** Невідомий код — «немає»: не лякаємо через збій формату. */
export const alertCode = (c: unknown): AlertCode | null => (c === 'A' || c === 'P' || c === 'N' ? c : c == null ? null : 'N');

const sameDay = (a: Date, b: Date) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

/** «14:05» сьогодні, «23 лист., 14:05» — іншого дня. */
export function alertTime(iso: string, now: Date): string {
  const t = new Date(iso);
  const hm = t.toLocaleTimeString('uk-UA', { hour: 'numeric', minute: '2-digit' });
  return sameDay(t, now) ? hm : `${t.toLocaleDateString('uk-UA', { day: 'numeric', month: 'short' })}, ${hm}`;
}

/** Текст плашки або null (тривоги немає чи стан невідомий). «Станом на» — щоб застарілі дані не видавались за поточні. */
export function alertBanner(a: AirAlert | null, now: Date): AlertBanner | null {
  if (!a || a.status === null || a.status === 'N') return null;
  const details = [a.since && `з ${alertTime(a.since, now)}`, a.checkedAt && `станом на ${alertTime(a.checkedAt, now)}`].filter(Boolean).join(' · ');
  if (a.status === 'A') {
    return { active: true, title: `🚨 Повітряна тривога · ${a.name}`, detail: [details, 'Пройдіть в укриття.'].filter(Boolean).join('. ') };
  }
  return { active: false, title: `⚠️ Тривога в частині: ${a.name}`, detail: details };
}

/** Коротша назва для списку: «… територіальна громада» → «… громада». На сервері лишається повна. */
export const shortName = (name: string): string => name.replace(' територіальна громада', ' громада');

/** Порядок українського алфавіту (за кодами символів «І» опинилась би після «Х»). */
const collator = new Intl.Collator('uk-UA');
export const ukOrder = (a: string, b: string): number => collator.compare(a, b);

/** Перший крок вибору: спершу окремі міста (Київ обирають найчастіше), далі області — за абеткою. */
export const topLevel = (locs: AlertLoc[]): AlertLoc[] =>
  locs
    .filter((l) => l.kind === 'oblast' || l.kind === 'city')
    .sort((a, b) => (a.kind === b.kind ? ukOrder(a.name, b.name) : a.kind === 'city' ? -1 : 1));

export interface RegionItem { loc: AlertLoc; raion: boolean }

/**
 * Райони з громадами під кожним, за абеткою. Пошук — за частиною назви (без регістру):
 * знайдений район показуємо з усіма громадами, інакше — лише знайдені громади з їхнім районом.
 */
export function regionItems(children: AlertLoc[], query: string): RegionItem[] {
  const q = query.trim().toLocaleLowerCase('uk-UA');
  const matches = (l: AlertLoc) => !q || l.name.toLocaleLowerCase('uk-UA').includes(q);
  const byRaion = new Map<number, AlertLoc[]>();
  for (const h of children) {
    if (h.kind !== 'hromada' || h.raion_uid == null) continue;
    byRaion.set(h.raion_uid, [...(byRaion.get(h.raion_uid) ?? []), h]);
  }
  return children
    .filter((l) => l.kind === 'raion')
    .sort((a, b) => ukOrder(a.name, b.name))
    .flatMap((raion) => {
      const inside = (byRaion.get(raion.uid) ?? []).sort((a, b) => ukOrder(shortName(a.name), shortName(b.name)));
      const shown = matches(raion) ? inside : inside.filter(matches);
      if (!matches(raion) && !shown.length) return [];
      return [{ loc: raion, raion: true }, ...shown.map((loc) => ({ loc, raion: false }))];
    });
}
