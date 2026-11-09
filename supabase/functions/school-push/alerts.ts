/**
 * Повітряні тривоги: чиста логіка без мережі й Deno — тестується у vitest (tests/unit/alerts.test.ts).
 *
 * Джерело — alerts.in.ua `/v1/iot/active_air_raid_alerts.json`: один рядок на всю країну,
 * де позиція символу = UID місця: «A» — тривога, «P» — у частині (для області/району), «N» — немає,
 * пробіл — UID не використовується.
 */

export type AlertStatus = 'A' | 'P' | 'N';

export interface AlertLocation {
  uid: number;
  name: string;
  kind: 'oblast' | 'city' | 'raion' | 'hromada';
  oblast_uid: number | null;
  raion_uid: number | null;
  /** Тривоги тут оголошують окремо від області/району (м. Київ, Харків, Запоріжжя). */
  separate: boolean;
}

export interface AlertState {
  uid: number;
  status: AlertStatus;
  since: string;
  checked_at: string;
}

export interface AlertEvent {
  uid: number;
  name: string;
  kind: 'start' | 'end';
  since: string;
}

export const ALERTS_URL = 'https://api.alerts.in.ua/v1/iot/active_air_raid_alerts.json';

/** Останній UID областей/Києва (31) — рядок має покривати хоча б їх. */
const MIN_UID = 31;

/**
 * Перевіряє відповідь API. null — відповідь підозріла (обрізана до потрібних UID, невідомі символи):
 * тоді нічого не змінюємо й нікого не сповіщаємо — краще пропустити хвилину, ніж надіслати хибний «відбій».
 */
export function parseStatuses(body: unknown, maxUid: number): string | null {
  if (typeof body !== 'string' || body.length <= Math.max(maxUid, MIN_UID)) return null;
  return /^[APN ]+$/.test(body) ? body : null;
}

const at = (s: string, uid: number | null): AlertStatus | null => {
  if (uid == null) return null;
  const c = s[uid];
  return c === 'A' || c === 'P' || c === 'N' ? c : null;
};

/**
 * Стан для вибраного місця з урахуванням ієрархії: тривога в області діє на всі її райони й громади,
 * тривога в районі — на його громади. Окремі міста (separate) — лише за власним статусом.
 * Для громади «частково» не буває: або тривога, або ні.
 */
export function effectiveStatus(loc: AlertLocation, s: string): AlertStatus {
  const own = at(s, loc.uid) ?? 'N';
  if (own === 'A') return 'A';
  if (loc.separate || loc.kind === 'oblast' || loc.kind === 'city') return own;
  if (at(s, loc.oblast_uid) === 'A') return 'A';
  if (loc.kind === 'raion') return own;
  if (at(s, loc.raion_uid) === 'A') return 'A';
  return 'N';
}

/**
 * Новий стан для кожного місця й події для сповіщень.
 * Подія — лише коли «тривога» з'явилась або зникла; P↔N — тиха зміна (без push).
 * Перший запис для місця (його щойно обрали) — без події: стан і так видно в застосунку.
 */
export function transitions(
  locations: AlertLocation[],
  statuses: string,
  previous: AlertState[],
  now: Date,
): { states: AlertState[]; events: AlertEvent[] } {
  const prev = new Map(previous.map((p) => [p.uid, p]));
  const iso = now.toISOString();
  const states: AlertState[] = [];
  const events: AlertEvent[] = [];
  for (const loc of locations) {
    const status = effectiveStatus(loc, statuses);
    const old = prev.get(loc.uid);
    const changed = !old || old.status !== status;
    const since = changed ? iso : old.since;
    states.push({ uid: loc.uid, status, since, checked_at: iso });
    if (!old) continue;
    if (old.status !== 'A' && status === 'A') events.push({ uid: loc.uid, name: loc.name, kind: 'start', since });
    if (old.status === 'A' && status !== 'A') events.push({ uid: loc.uid, name: loc.name, kind: 'end', since });
  }
  return { states, events };
}

/** Текст сповіщення. Без паніки, але чітко; «відбій» — окремим, спокійним текстом. */
export function alertText(e: AlertEvent): { title: string; body: string } {
  return e.kind === 'start'
    ? { title: '🚨 Повітряна тривога', body: `${e.name}. Пройдіть в укриття.` }
    : { title: '✅ Відбій тривоги', body: e.name };
}
