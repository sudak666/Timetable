/** Чиста доменна логіка нагород — без DOM і мережі, покрита unit-тестами. */

export type Role = 'parent' | 'child';
export interface Member { user_id: string; role: Role; name: string; goal_title: string; goal_amount: number; avatar: string; theme: string }
export interface Grade { id: string; child_id: string; subject: string; grade: number; date: string; status: 'pending' | 'approved'; amount: number | null; created_at: string }
/** src: 'star' — зірка за поведінку від батьків, 'quiz' — гроші за вікторину (нараховує сервер). Гроші — як звичайний бонус. */
export interface Ledger { id: string; child_id: string; kind: 'bonus' | 'payout'; amount: number; note: string; date: string; created_at: string; src?: 'star' | 'quiz' | null }
export interface Challenge { id: string; child_id: string; title: string; subject: string | null; min_grade: number; need: number; reward: number; start_date: string; end_date: string }
/** created_by ставить сервер; null/немає — старі записи (вважаються дитячими). */
export interface Homework { id: string; child_id: string; subject: string; due: string; text: string; done: boolean; created_by?: string | null }
export interface Streak { len: number; min: number; bonus: number }
export type Rates = Record<number, number>;
/** Ціни зірки за поведінку й правильної відповіді у вікторині (ключі star_price / quiz_price у school_families.rates). */
export interface Prices { star: number; quiz: number }

export const DEFAULT_RATES: Rates = { 12: 300, 11: 250, 10: 200, 9: 150, 8: 100, 7: 70, 6: 0, 5: -70, 4: -100, 3: -150, 2: -200, 1: -300 };
export const DEFAULT_STREAK: Streak = { len: 5, min: 7, bonus: 50 };
export const DEFAULT_PRICES: Prices = { star: 50, quiz: 20 };
/** Скільки зірок батьки можуть поставити за день і скільки відповідей вікторини оплачується (те саме — на сервері). */
export const STARS_PER_DAY = 5;
export const QUIZ_PAID_PER_DAY = 3;
export const MAX_PRICE = 1000;
export const XP_PER_LEVEL = 50;

export interface AutoReward { id: string; date: string; created_at: string; amount: number; note: string; icon: string }

export const byTime = <T extends { date: string; created_at: string }>(a: T, b: T): number =>
  a.date === b.date ? (a.created_at < b.created_at ? -1 : a.created_at > b.created_at ? 1 : 0) : a.date < b.date ? -1 : 1;

/** «Зараховано» (grade = 0): платиться як 7, у середній бал, XP, серії та челенджі не входить. */
export const PASS = 0;
export const PASS_AS = 7;
export const isScored = (g: Pick<Grade, 'grade'>): boolean => g.grade !== PASS;
export const rateOf = (g: number, rates: Rates): number => rates[g === PASS ? PASS_AS : g] ?? 0;
export const gradeLabel = (g: number): string => (g === PASS ? 'Зар.' : String(g));

/** Сума за оцінку: зафіксована при підтвердженні, інакше — поточний курс. */
export const gradeValue = (g: Pick<Grade, 'amount' | 'grade'>, rates: Rates): number => g.amount ?? rateOf(g.grade, rates);

export function parseSettings(raw: unknown): { rates: Rates; streak: Streak; prices: Prices } {
  const o = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>;
  const rates: Rates = { ...DEFAULT_RATES };
  for (const [k, v] of Object.entries(o)) if (/^\d+$/.test(k) && Number.isFinite(Number(v))) rates[Number(k)] = Math.trunc(Number(v));
  const num = (v: unknown, d: number) => (Number.isFinite(Number(v)) && v !== null && v !== '' ? Number(v) : d);
  return {
    rates,
    streak: {
      len: Math.max(2, num(o.streak_len, DEFAULT_STREAK.len)),
      min: Math.min(12, Math.max(1, num(o.streak_min, DEFAULT_STREAK.min))),
      bonus: Math.max(0, num(o.streak_bonus, DEFAULT_STREAK.bonus)),
    },
    prices: {
      star: Math.min(MAX_PRICE, Math.max(0, Math.trunc(num(o.star_price, DEFAULT_PRICES.star)))),
      quiz: Math.min(MAX_PRICE, Math.max(0, Math.trunc(num(o.quiz_price, DEFAULT_PRICES.quiz)))),
    },
  };
}

/** Зірки за поведінку дитини за день (найновіша — остання). */
export const starsOn = (ledger: Ledger[], kid: string | null, date: string): Ledger[] =>
  ledger.filter((l) => l.src === 'star' && l.child_id === kid && l.date === date).sort(byTime);

export const challengeHits = (c: Challenge, approvedSorted: Grade[]): Grade[] =>
  approvedSorted.filter((g) => isScored(g) && g.date >= c.start_date && g.date <= c.end_date && g.grade >= c.min_grade && (!c.subject || g.subject === c.subject));

/** Автоматичні нагороди за серії та виконані челенджі (детерміновано з підтверджених оцінок). */
export function autoRewards(approved: Grade[], challenges: Challenge[], streak: Streak): AutoReward[] {
  const s = [...approved].sort(byTime);
  const scored = s.filter(isScored);
  const out: AutoReward[] = [];
  if (streak.bonus > 0) {
    let run = 0;
    for (const g of scored) {
      run = g.grade >= streak.min ? run + 1 : 0;
      if (run === streak.len) {
        out.push({ id: 's' + g.id, date: g.date, created_at: g.created_at, amount: streak.bonus, note: `Серія: ${streak.len} оцінок поспіль ≥ ${streak.min}`, icon: '🔥' });
        run = 0;
      }
    }
  }
  for (const c of challenges) {
    const hit = challengeHits(c, s)[c.need - 1];
    if (hit) out.push({ id: 'c' + c.id, date: hit.date, created_at: hit.created_at, amount: c.reward, note: 'Челендж: ' + c.title, icon: '🏅' });
  }
  return out;
}

/** Поточна серія (скидається після нарахування бонусу). */
export function currentRun(approved: Grade[], streak: Streak): number {
  let run = 0;
  for (const g of approved.filter(isScored).sort(byTime)) {
    run = g.grade >= streak.min ? run + 1 : 0;
    if (run === streak.len) run = 0;
  }
  return run;
}

export function bestTenRun(approved: Grade[]): number {
  let run = 0, best = 0;
  for (const g of approved.filter(isScored).sort(byTime)) {
    run = g.grade >= 10 ? run + 1 : 0;
    best = Math.max(best, run);
  }
  return best;
}

export interface Totals { today: number; week: number; month: number; earned: number; paid: number; balance: number }
export function totals(approved: Grade[], ledger: Ledger[], auto: AutoReward[], rates: Rates, today: string, weekStart: string): Totals {
  const month = today.slice(0, 7);
  const items = [
    ...approved.map((g) => ({ date: g.date, v: gradeValue(g, rates) })),
    ...ledger.filter((l) => l.kind === 'bonus').map((l) => ({ date: l.date, v: l.amount })),
    ...auto.map((a) => ({ date: a.date, v: a.amount })),
  ];
  const sum = (f: (d: string) => boolean) => items.reduce((t, x) => (f(x.date) ? t + x.v : t), 0);
  const earned = sum(() => true);
  const paid = ledger.filter((l) => l.kind === 'payout').reduce((t, l) => t + l.amount, 0);
  return {
    today: sum((d) => d === today),
    week: sum((d) => d >= weekStart && d <= today),
    month: sum((d) => d.startsWith(month)),
    earned,
    paid,
    balance: earned - paid,
  };
}

export const xpOf = (approved: Grade[]): number => approved.reduce((t, g) => t + g.grade, 0);
export const levelOf = (xp: number): number => Math.floor(xp / XP_PER_LEVEL) + 1;

export const gradeColor = (g: number): string => (g === PASS ? '#5b6472' : g >= 10 ? '#00866b' : g >= 7 ? '#0869b5' : g >= 4 ? '#8a5c00' : '#c62828');

export const AVATARS: readonly [string, number][] = [['🦊', 1], ['🐼', 1], ['🐸', 1], ['🐙', 2], ['🐯', 2], ['🦁', 3], ['🐺', 3], ['🦉', 4], ['🐧', 4], ['🦄', 5], ['🤖', 6], ['👽', 7], ['🦖', 8], ['🐲', 10], ['👾', 12], ['🦈', 15]];
/** Папір сторінки → рівень (як Trophies.PAPERS в Android). Ключ — у localStorage, лише на цьому пристрої. */
export const PAPERS: readonly [key: string, title: string, level: number][] = [['grid', 'Клітинка', 1], ['lines', 'Лінійка', 2], ['dots', 'Крапки', 4], ['graph', 'Міліметрівка', 6], ['kraft', 'Крафт', 9]];
/** Обраний папір, якщо він уже відкритий на рівні; інакше — клітинка. */
export const paperFor = (key: string | null, lv: number): string => PAPERS.find(([k, , l]) => k === key && l <= lv)?.[0] ?? 'grid';
export const THEMES: readonly [string, number][] = [['#1f3fa6', 1], ['#0869b5', 1], ['#00866b', 2], ['#c2185b', 3], ['#b84a00', 4], ['#8a5c00', 5], ['#c62828', 6], ['#2d3436', 8]];
