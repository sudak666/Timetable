import { html, nothing, type TemplateResult } from 'lit-html';
import { classMap } from 'lit-html/directives/class-map.js';
import { QUIZ_PAID_PER_DAY } from '../features/rewards';
import { emo } from '../lib/emoji';
import { set, state } from '../state';
import { boom } from '../ui/confetti';
import { toast } from '../ui/toast';

/**
 * Вікторина на перерві. Питання й правильні відповіді — лише на сервері (school_quiz_round / school_quiz_answer):
 * відповідь перевіряє сервер і сам нараховує дитині гроші — за першу відповідь на питання, до 3 на день.
 */
interface Q { id: number; q: string; opts: string[] }
interface Answer { correct: boolean; right: string; paid: number; left: number }

let qs: Q[] = [], qi = 0, ok = 0, earned = 0, left: number | null = null;
let picked: string | null = null, right: string | null = null;
let phase: 'idle' | 'loading' | 'error' = 'idle';

const isRow = (r: unknown): r is { id: number; q: string; options: string[] } =>
  !!r && typeof r === 'object' && typeof (r as { id?: unknown }).id === 'number' && typeof (r as { q?: unknown }).q === 'string'
  && Array.isArray((r as { options?: unknown }).options);

export async function quizStart(): Promise<void> {
  phase = 'loading';
  qs = [];
  qi = ok = earned = 0;
  picked = right = null;
  set({});
  // api.ts — окремим шматком: гостьова сторінка розкладу не вантажить код сервера.
  const { sb } = await import('../api');
  const { data, error } = await sb.rpc('school_quiz_round', { n: 3 });
  const rows = Array.isArray(data) ? data.filter(isRow) : [];
  if (error || !rows.length) {
    phase = 'error';
  } else {
    qs = rows.map((r) => ({ id: r.id, q: r.q, opts: r.options }));
    phase = 'idle';
  }
  set({});
}

async function answer(o: string, e: MouseEvent) {
  const q = qs[qi];
  if (picked || !q) return;
  picked = o;
  set({});
  const { sb } = await import('../api');
  const { data, error } = await sb.rpc('school_quiz_answer', { p_id: q.id, p_answer: o });
  const a = data as Answer | null;
  if (error || !a || typeof a.right !== 'string') {
    picked = null;
    toast('Немає зв’язку — відповідь не зараховано. Спробуй ще раз 🙏', 'err');
    set({});
    return;
  }
  right = a.right;
  left = a.left;
  if (a.correct) {
    ok++;
    boom(e.clientX, e.clientY, a.paid > 0 ? 50 : 30);
  }
  earned += a.paid;
  set({});
  setTimeout(() => { qi++; picked = right = null; set({}); }, 900);
}

/** Підказка під заголовком: скільки коштує правильна відповідь і чи ще платимо сьогодні. */
function priceNote(): TemplateResult | typeof nothing {
  const price = state.prices.quiz;
  if (!price) return nothing;
  if (state.role !== 'child') return html`<p class="msg" style="margin:0">Гроші за правильні відповіді отримує дитина: +${price} ₴, до ${QUIZ_PAID_PER_DAY} на день</p>`;
  if (left === 0) return html`<p class="msg" style="margin:0">${emo('✅')} Сьогодні за вікторину вже заплачено — грай для інтересу</p>`;
  return html`<p class="msg" style="margin:0">Правильна відповідь — <b>+${price} ₴</b> (до ${QUIZ_PAID_PER_DAY} на день)</p>`;
}

export function quizView(): TemplateResult {
  // Питання — на сервері, тож без входу вікторини немає (гостям показуємо лише розклад).
  if (!state.me) return html`<p class="msg">${emo('🧠')} Увійди у вкладці «Оцінки» — і грай у вікторину: за правильні відповіді дитина отримує гроші ${emo('💰')}</p>`;
  if (phase === 'idle' && !qs.length) void quizStart();
  if (phase === 'loading' || (phase === 'idle' && !qs.length)) return html`<p class="msg" aria-live="polite">Завантажую питання…</p>`;
  if (phase === 'error') {
    return html`<p class="msg" aria-live="polite">${emo('📡')} Вікторина працює з інтернетом — не вдалося завантажити питання</p>
      <button type="button" @click=${() => void quizStart()}>Спробувати ще раз</button>`;
  }
  if (qi >= qs.length) {
    const verdict = ok === qs.length ? '🏆 Ідеально!' : ok ? '👍 Непогано!' : '💪 Наступного разу вийде!';
    return html`<p class="q" aria-live="polite">${emo(verdict)} ${ok}/${qs.length}${earned > 0 ? html` — <b class="pos">+${earned} ₴</b> у гаманець ${emo('💰')}` : nothing}</p>
      ${priceNote()}<button type="button" @click=${() => void quizStart()}>Ще раз</button>`;
  }
  const q = qs[qi]!;
  return html`${priceNote()}<p class="msg" style="margin:0">Питання ${qi + 1} з ${qs.length}</p><p class="q" id="qz-q">${q.q}</p>
    <div class="ans" role="group" aria-labelledby="qz-q">${q.opts.map((o) => html`<button type="button" ?disabled=${!!picked}
      class=${classMap({ ok: !!right && o === right, no: !!right && picked === o && o !== right })}
      @click=${(e: MouseEvent) => void answer(o, e)}>${o}</button>`)}</div>`;
}
