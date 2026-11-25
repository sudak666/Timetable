/**
 * Текст сповіщення дитині про новий запис гаманця. Чиста логіка без мережі — тести у vitest (tests/unit/ledger.test.ts).
 * null — не сповіщати: гроші за вікторину дитина бачить одразу, бо сама щойно відповіла.
 */
export interface LedgerRow { kind: string; amount: number; note?: string | null; src?: string | null }

const uah = (v: number) => (v > 0 ? "+" : "") + v + " ₴";

export function ledgerPush(r: LedgerRow): { title: string; body: string } | null {
  if (r.src === "quiz") return null;
  if (r.src === "star") return { title: `⭐ Зірка за поведінку: ${uah(r.amount)}`, body: "Так тримати! 💪" };
  if (r.kind === "payout") return { title: `💸 Виплачено ${r.amount} ₴`, body: r.note || "" };
  return { title: r.amount >= 0 ? `🎁 Бонус ${uah(r.amount)}` : `⚠️ Штраф ${r.amount} ₴`, body: r.note || "" };
}
