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
 * Батькам замість гри — сьогоднішні відповіді дитини (school_quiz_today); пограти самим можна кнопкою.
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

/** Сьогоднішні відповіді дитини для батьків. */
export interface KidAnswer { quiz_id: number; q: string; answer: string | null; right_answer: string; correct: boolean; paid: number }

const isKidAnswer = (r: unknown): r is KidAnswer =>
  !!r && typeof r === 'object' && typeof (r as { q?: unknown }).q === 'string' && typeof (r as { right_answer?: unknown }).right_answer === 'string'
  && typeof (r as { correct?: unknown }).correct === 'boolean';

let kidKey: string | null = null, kidRows: KidAnswer[] = [], kidPhase: 'idle' | 'loading' | 'error' = 'idle', selfPlay = false;

/** Ключ кешу: дитина + день (опівночі відповіді вчорашні — перезавантажуємо). */
const keyOf = (kid: string) => kid + '|' + state.now.toDateString();

export async function loadKidAnswers(kid: string): Promise<void> {
  const key = keyOf(kid);
  if (kidKey !== key) kidRows = [];
  kidKey = key;
  kidPhase = 'loading';
  set({});
  let rows: KidAnswer[] | null = null;
  try {
    const { sb } = await import('../api');
    const { data, error } = await sb.rpc('school_quiz_today', { p_child: kid });
    if (!error) rows = Array.isArray(data) ? data.filter(isKidAnswer) : [];
  } catch { /* офлайн: шматок api.ts не завантажився */ }
  if (kidKey !== key) return; // поки вантажили, батьки обрали іншу дитину
  if (rows) kidRows = rows;
  kidPhase = rows ? 'idle' : 'error';
  set({});
}

/** Скільки «відповідь/відповіді/відповідей». */
export function answersWord(n: number): string {
  const d = n % 10, h = n % 100;
  if (d === 1 && h !== 11) return 'відповідь';
  if (d >= 2 && d <= 4 && (h < 12 || h > 14)) return 'відповіді';
  return 'відповідей';
}

function parentView(kid: string): TemplateResult {
  if (kidKey !== keyOf(kid)) void loadKidAnswers(kid);
  const name = state.members.find((m) => m.user_id === kid)?.name || 'Дитина';
  const play = html`<button type="button" @click=${() => { selfPlay = true; set({}); }}>Пограти самому</button>`;
  const refresh = html`<button type="button" @click=${() => void loadKidAnswers(kid)}>Оновити</button>`;
  if (kidPhase === 'loading' && !kidRows.length) return html`<p class="msg" aria-live="polite">Завантажую відповіді…</p>`;
  if (kidPhase === 'error') {
    return html`<p class="msg" aria-live="polite">${emo('📡')} ${name}: не вдалося завантажити відповіді — потрібен інтернет</p>
      <div class="row">${play}<button type="button" @click=${() => void loadKidAnswers(kid)}>Спробувати ще раз</button></div>`;
  }
  if (!kidRows.length) {
    return html`<p class="msg" aria-live="polite">${emo('🧠')} ${name}: сьогодні відповідей у вікторині ще немає. Після кожних 3 відповідей надійде сповіщення.</p>
      <div class="row">${play}${refresh}</div>`;
  }
  const ok = kidRows.filter((r) => r.correct).length, paid = kidRows.reduce((s, r) => s + Math.max(0, r.paid), 0);
  return html`<p class="q" aria-live="polite">${name} сьогодні: ${kidRows.length} ${answersWord(kidRows.length)}, ${ok} правильно${paid > 0 ? html` — <b class="pos">+${paid} ₴</b>` : nothing}</p>
    <ul class="qa">${kidRows.map((r) => html`<li class=${r.correct ? 'ok' : 'no'}>
      <span class="qa-q">${emo(r.correct ? '✅' : '❌')} ${r.q}</span>
      <span class="qa-a">${r.answer ?? '—'}${r.correct ? nothing : html` · правильно: <b>${r.right_answer}</b>`}${r.paid > 0 ? html` · <b class="pos">+${r.paid} ₴</b>` : nothing}</span></li>`)}</ul>
    <div class="row">${play}${refresh}</div>`;
}

export function quizView(): TemplateResult {
  // Питання — на сервері, тож без входу вікторини немає (гостям показуємо лише розклад).
  if (!state.me) return html`<p class="msg">${emo('🧠')} Увійди у вкладці «Оцінки» — і грай у вікторину: за правильні відповіді дитина отримує гроші ${emo('💰')}</p>`;
  if (state.role === 'parent' && state.kid && !selfPlay) return parentView(state.kid);
  const back = state.role === 'parent' && state.kid
    ? html`<button type="button" @click=${() => { selfPlay = false; set({}); }}>Відповіді дитини</button>` : nothing;
  if (phase === 'idle' && !qs.length) void quizStart();
  if (phase === 'loading' || (phase === 'idle' && !qs.length)) return html`<p class="msg" aria-live="polite">Завантажую питання…</p>`;
  if (phase === 'error') {
    return html`<p class="msg" aria-live="polite">${emo('📡')} Вікторина працює з інтернетом — не вдалося завантажити питання</p>
      <div class="row">${back}<button type="button" @click=${() => void quizStart()}>Спробувати ще раз</button></div>`;
  }
  if (qi >= qs.length) {
    const verdict = ok === qs.length ? '🏆 Ідеально!' : ok ? '👍 Непогано!' : '💪 Наступного разу вийде!';
    return html`<p class="q" aria-live="polite">${emo(verdict)} ${ok}/${qs.length}${earned > 0 ? html` — <b class="pos">+${earned} ₴</b> у гаманець ${emo('💰')}` : nothing}</p>
      ${priceNote()}<div class="row">${back}<button type="button" @click=${() => void quizStart()}>Ще раз</button></div>`;
  }
  const q = qs[qi]!;
  return html`${priceNote()}<p class="msg" style="margin:0">Питання ${qi + 1} з ${qs.length}</p><p class="q" id="qz-q">${q.q}</p>
    <div class="ans" role="group" aria-labelledby="qz-q">${q.opts.map((o) => html`<button type="button" ?disabled=${!!picked}
      class=${classMap({ ok: !!right && o === right, no: !!right && picked === o && o !== right })}
      @click=${(e: MouseEvent) => void answer(o, e)}>${o}</button>`)}</div>${back === nothing ? nothing : html`<div class="row">${back}</div>`}`;
}
