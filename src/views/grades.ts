import { html, nothing, type TemplateResult } from 'lit-html';
import { classMap } from 'lit-html/directives/class-map.js';
import { live } from 'lit-html/directives/live.js';
import { styleMap } from 'lit-html/directives/style-map.js';
import { enter, errText, loadFamily, sb, signOut } from '../api';
import { subjEmoji } from '../data/schedule';
import {
  AVATARS, THEMES, XP_PER_LEVEL, autoRewards, bestTenRun, byTime, challengeHits, currentRun, gradeColor, gradeValue, levelOf, totals, xpOf,
  type Grade,
} from '../features/rewards';
import { emo } from '../lib/emoji';
import { addDays, fmtDay, iso, mondayOf, nextLessonDate } from '../lib/time';
import { set, state, store, type GameStyle } from '../state';
import { applyStyle } from '../ui/theme';
import { boom } from '../ui/confetti';
import { alert, confirm, prompt } from '../ui/dialog';
import { dateLabel, pickDate, pickSubject, subjLabel } from '../ui/pickers';
import { closePop, openPop } from '../ui/popover';
import { installApp, togglePush } from '../pwa';

const uah = (v: number) => (v > 0 ? '+' : '') + v + ' ₴';
const tone = (v: number) => (v > 0 ? 'pos' : v < 0 ? 'neg' : '');
const REDIRECT = () => location.origin + location.pathname;

/** Виконує запит і перезавантажує дані; помилку показує в діалозі. */
async function run(p: PromiseLike<{ error: unknown }>, after?: () => void): Promise<boolean> {
  const { error } = await p;
  if (error) { await alert(errText(error)); return false; }
  after?.();
  await loadFamily().catch(() => undefined);
  return true;
}

/* ---------------- похідні дані ---------------- */
function derive() {
  const kid = state.kid;
  const G = state.grades.filter((g) => g.child_id === kid);
  const A = G.filter((g) => g.status === 'approved');
  const pend = G.filter((g) => g.status === 'pending');
  const L = state.ledger.filter((l) => l.child_id === kid);
  const CH = state.challenges.filter((c) => c.child_id === kid);
  const auto = autoRewards(A, CH, state.streak);
  const today = iso(state.now);
  const t = totals(A, L, auto, state.rates, today, iso(mondayOf(state.now)));
  const xp = xpOf(A);
  return { G, A, pend, L, CH, auto, t, xp, lv: levelOf(xp), run: currentRun(A, state.streak), today };
}
type D = ReturnType<typeof derive>;
const me = () => state.members.find((m) => m.user_id === state.me?.id);
const kidMember = () => state.members.find((m) => m.user_id === state.kid);

/* ---------------- профіль зверху ---------------- */
export function profileBar(): TemplateResult | typeof nothing {
  if (state.view !== 'main' || !state.me) return nothing;
  const d = derive(), m = me(), par = state.role === 'parent';
  const hw = state.homework.filter((h) => h.child_id === state.kid && !h.done);
  const late = hw.filter((h) => h.due < d.today).length;
  const pend = state.grades.filter((g) => g.status === 'pending' && (par || g.child_id === state.me!.id)).length;
  const go = (id: string) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  return html`<section class="prof" aria-label="Профіль">
    <button type="button" class="pa" aria-haspopup="dialog" aria-label="Аватар, колір і стиль" @click=${avatarPop}>${emo(m?.avatar || (par ? '👨‍👩‍👦' : '🧒'))}</button>
    <div class="pn"><b>${m?.name || state.me.email}</b>
      <small>${par && state.kid ? html`${emo('👀')} ${kidMember()?.name || 'Дитина'} · ` : nothing}Рівень ${d.lv} · ${d.xp} XP</small>
      <div class="pl" role="progressbar" aria-label="Прогрес рівня" aria-valuemin="0" aria-valuemax=${XP_PER_LEVEL} aria-valuenow=${d.xp % XP_PER_LEVEL}><i style=${styleMap({ width: (d.xp % XP_PER_LEVEL) * (100 / XP_PER_LEVEL) + '%' })}></i></div></div>
    <div class="ps">
      <button type="button" class="st" @click=${() => go('gl')}>${emo('💰')}<span>${d.t.balance} ₴<small>до виплати</small></span></button>
      <button type="button" class="st" @click=${() => go('streak')}>${emo('🔥')}<span>${d.run}<small>серія</small></span></button>
      <button type="button" class=${classMap({ st: true, alert: late > 0 })} @click=${() => go('hwl')}>${emo('📝')}<span>${hw.length}<small>${late ? 'є борги!' : 'домашка'}</small></span></button>
      ${pend ? html`<button type="button" class="st alert" @click=${() => go('gl')}>${emo('⏳')}<span>${pend}<small>${par ? 'підтвердити' : 'чекає'}</small></span></button>` : nothing}
    </div>
  </section>`;
}

export function laneLabel(): unknown {
  const k = kidMember();
  if (state.view === 'main' && k?.goal_amount) return emo(`🎯 ${k.goal_title || 'Мрія'}: ${Math.max(0, derive().t.balance)} / ${k.goal_amount} ₴`);
  return emo('⚡ Електробайк мрії');
}

/* ---------------- аватар / колір / стиль ---------------- */
function avatarPop(e: Event) {
  const lv = state.role === 'parent' ? 99 : levelOf(xpOf(state.grades.filter((g) => g.child_id === state.me?.id && g.status === 'approved')));
  const upd = async (patch: { avatar?: string; theme?: string }) => {
    closePop();
    await run(sb.from('school_members').update(patch).eq('family_id', state.family!.id).eq('user_id', state.me!.id));
  };
  const setStyle = (s: GameStyle) => { store.set('style', s); applyStyle(s); set({ style: s }); };
  openPop(e.currentTarget as HTMLElement, (redraw) => {
    const cur = me();
    return html`<h3 class="h6">Стиль</h3>
      <div class="stylebtn">${([['', '✨ Звичайний'], ['arena', '🏆 Арена'], ['blocks', '⛏️ Блоки']] as [GameStyle, string][]).map(([v, l]) =>
        html`<button type="button" aria-pressed=${state.style === v} class=${classMap({ on: state.style === v })} @click=${() => { setStyle(v); redraw(); }}>${emo(l)}</button>`)}</div>
      <h3 class="h6">Аватар${state.role === 'child' ? ` · рівень ${lv}` : ''}</h3>
      <div class="avg">${AVATARS.map(([a, l]) => html`<button type="button" ?disabled=${l > lv} aria-pressed=${cur?.avatar === a} aria-label=${l > lv ? `Відкриється на рівні ${l}` : 'Вибрати аватар'} data-l=${'🔒' + l} class=${classMap({ on: cur?.avatar === a })} @click=${() => upd({ avatar: a })}>${emo(a)}</button>`)}</div>
      <h3 class="h6">Колір сайту</h3>
      <div class="avg">${THEMES.map(([c, l]) => html`<button type="button" class=${classMap({ sw: true, on: cur?.theme === c })} ?disabled=${l > lv} aria-pressed=${cur?.theme === c} aria-label=${l > lv ? `Колір відкриється на рівні ${l}` : `Колір ${c}`} data-l=${'🔒' + l} style=${styleMap({ background: c })} @click=${() => upd({ theme: c })}></button>`)}</div>`;
  }, 'Аватар, колір і стиль');
}

/* ---------------- вхід / сім'я ---------------- */
function authView(): TemplateResult {
  const msg = state.authMsg;
  const email = () => (document.getElementById('gEmail') as HTMLInputElement).value.trim();
  const pass = () => (document.getElementById('gPass') as HTMLInputElement).value;
  const login = async (e: Event) => {
    e.preventDefault();
    const { error } = await sb.auth.signInWithPassword({ email: email(), password: pass() });
    if (error) set({ authMsg: { text: /invalid/i.test(error.message) ? 'Невірна пошта або пароль' : /confirm/i.test(error.message) ? 'Підтверди пошту — лист уже надіслано' : error.message } });
  };
  const signup = async () => {
    const f = document.getElementById('gMail') as HTMLFormElement;
    if (!f.reportValidity()) return;
    const { data, error } = await sb.auth.signUp({ email: email(), password: pass(), options: { emailRedirectTo: REDIRECT() } });
    if (error) return set({ authMsg: { text: error.message } });
    if (!data.session) set({ authMsg: { text: 'Готово! Перевір пошту й натисни посилання для підтвердження 📬', ok: true } });
  };
  const google = async () => {
    const { error } = await sb.auth.signInWithOAuth({ provider: 'google', options: { redirectTo: REDIRECT(), queryParams: { prompt: 'select_account' } } });
    if (error) set({ authMsg: { text: error.message } });
  };
  return html`<p class="lead">Увійди, щоб записувати оцінки й отримувати нагороди.</p>
    <button type="button" class="wide" @click=${google}><svg viewBox="0 0 48 48" width="18" height="18" aria-hidden="true"><path fill="#FFC107" d="M43.6 20.5H42V20H24v8h11.3C33.7 32.7 29.2 36 24 36c-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.4-.4-3.5z"/><path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 7.9 3.1l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-8l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.5H42V20H24v8h11.3c-.8 2.2-2.2 4.2-4.1 5.6l6.2 5.2C37 39.2 44 34 44 24c0-1.3-.1-2.4-.4-3.5z"/></svg> Увійти через Google</button>
    <div class="or">або поштою</div>
    <form class="form" id="gMail" @submit=${login}>
      <label class="sr-only" for="gEmail">Пошта</label><input type="email" id="gEmail" placeholder="пошта" required autocomplete="email">
      <label class="sr-only" for="gPass">Пароль</label><input type="password" id="gPass" placeholder="пароль (від 6 символів)" minlength="6" required autocomplete="current-password">
      <button>Увійти</button><button type="button" @click=${signup}>Зареєструватись</button>
    </form>
    ${msg ? html`<p class=${classMap({ msg: true, pos: !!msg.ok, neg: !msg.ok })} role="status">${emo(msg.text)}</p>` : nothing}`;
}

function joinView(): TemplateResult {
  const name = () => (document.getElementById('jName') as HTMLInputElement).value.trim();
  const create = async () => {
    const { error } = await sb.rpc('school_create_family', { p_name: 'Сім’я ' + name(), p_parent_name: name() });
    if (error) return set({ authMsg: { text: error.message } });
    await enter();
  };
  const join = async () => {
    const code = (document.getElementById('jCode') as HTMLInputElement).value.trim();
    if (!code) return set({ authMsg: { text: 'Введи код' } });
    const { error } = await sb.rpc('school_join_family', { p_code: code, p_name: name() });
    if (error) return set({ authMsg: { text: /Невірний/.test(error.message) ? 'Невірний код' : error.message } });
    await enter();
  };
  return html`<p class="lead">Ти ще не в сім'ї. Батьки створюють сім'ю, дитина приєднується за кодом.</p>
    <div class="form"><label class="sr-only" for="jName">Ім'я</label><input id="jName" placeholder="Твоє ім'я" maxlength="40" .value=${state.me?.fullName ?? ''}></div>
    <div class="form"><button type="button" @click=${create}>${emo('👨‍👩‍👦 Я батько/мати — створити сім’ю')}</button></div>
    <div class="form"><label class="sr-only" for="jCode">Код запрошення</label><input id="jCode" placeholder="Код запрошення" maxlength="10" autocapitalize="characters"><button type="button" @click=${join}>${emo('🔑 Приєднатись')}</button></div>
    ${state.authMsg ? html`<p class="msg neg" role="status">${state.authMsg.text}</p>` : nothing}`;
}

/* ---------------- головна ---------------- */
async function addGrade(g: number, e: MouseEvent) {
  if (!state.kid) return;
  const par = state.role === 'parent';
  const ok = await run(sb.from('school_grades').insert({
    family_id: state.family!.id, child_id: state.kid, subject: state.form.subject, grade: g, date: state.form.date || iso(new Date()),
    ...(par ? { status: 'approved', amount: state.rates[g] ?? 0 } : {}),
  }));
  if (ok && g >= 10) boom(e.clientX, e.clientY, g === 12 ? 150 : 70);
}
const approve = (x: Grade, e: MouseEvent) => run(sb.from('school_grades').update({ status: 'approved', amount: state.rates[x.grade] ?? 0 }).eq('id', x.id), () => x.grade >= 10 && boom(e.clientX, e.clientY, 80));
const unapprove = async (x: Grade) => (await confirm('Скасувати підтвердження? Оцінка знову стане «чекає».')) && run(sb.from('school_grades').update({ status: 'pending', amount: null }).eq('id', x.id));
const del = async (table: 'school_grades' | 'school_ledger' | 'school_homework' | 'school_challenges', id: string, q = 'Видалити запис?') => (await confirm(q)) && run(sb.from(table).delete().eq('id', id));

function summary(d: D): TemplateResult {
  const cell = (l: string, v: number) => html`<div><small>${l}</small><b class=${tone(v)}>${uah(v)}</b></div>`;
  const wA = d.A.filter((g) => g.date >= iso(mondayOf(state.now)));
  const best = bestTenRun(d.A);
  const badges: [string, boolean][] = [['🎯 Перша оцінка', d.A.length > 0], ['👑 Перша 12', d.A.some((g) => g.grade === 12)], ['🔥 3 десятки поспіль', best >= 3], ['🚀 5 поспіль ≥10', best >= 5], ['📚 20 оцінок', d.A.length >= 20], ['💯 50 оцінок', d.A.length >= 50], ['💰 Заробив 1000 ₴', d.t.earned >= 1000], ['🌈 Тиждень без мінусів', wA.length >= 3 && wA.every((g) => gradeValue(g, state.rates) >= 0)]];
  return html`<div class="sum">${cell('Сьогодні', d.t.today)}${cell('Цей тиждень', d.t.week)}${cell('Цей місяць', d.t.month)}${cell('До виплати', d.t.balance)}</div>
    <div class="lvl">Рівень ${d.lv} · ${d.xp} XP <small>(ще ${XP_PER_LEVEL - (d.xp % XP_PER_LEVEL)} до наступного)</small>
      <div class="bar" role="progressbar" aria-label="Прогрес рівня" aria-valuemin="0" aria-valuemax=${XP_PER_LEVEL} aria-valuenow=${d.xp % XP_PER_LEVEL}><i style=${styleMap({ width: (d.xp % XP_PER_LEVEL) * 2 + '%' })}></i></div></div>
    ${road(d.lv)}
    <ul class="badges" aria-label="Досягнення">${badges.map(([t, ok]) => html`<li class=${classMap({ bd: true, got: ok })}>${emo(t)}<span class="sr-only">${ok ? ' — отримано' : ' — ще ні'}</span></li>`)}</ul>`;
}

function road(lv: number): TemplateResult {
  const byLv = new Map<number, TemplateResult>();
  for (const [a, l] of AVATARS) if (!byLv.has(l)) byLv.set(l, html`<div class="ri">${emo(a)}</div>`);
  for (const [c, l] of THEMES) if (!byLv.has(l)) byLv.set(l, html`<div class="rsw" style=${styleMap({ background: c })}></div>`);
  const levels = [...byLv.keys()].filter((l) => l > 1).sort((a, b) => a - b), next = levels.find((l) => l > lv);
  return html`<ol class="road" aria-label="Шлях нагород" tabindex="0">${levels.map((l) => html`<li class=${classMap({ rs: true, got: l <= lv, lock: l > lv, next: l === next })}>${byLv.get(l)}<b>Рівень ${l}</b><small>${l <= lv ? 'відкрито' : (l - 1) * XP_PER_LEVEL + ' XP'}</small></li>`)}</ol>`;
}

function cards(d: D): TemplateResult {
  const k = kidMember(), ga = k?.goal_amount ?? 0, bal = Math.max(0, d.t.balance), gp = ga ? Math.min(100, (bal / ga) * 100) : 0;
  const editGoal = async () => {
    const t = await prompt('На що збираєш?', k?.goal_title ?? '', 'text');
    if (t === null) return;
    const a = await prompt('Скільки коштує, ₴?', k?.goal_amount || '');
    if (a === null) return;
    await run(sb.from('school_members').update({ goal_title: t.trim().slice(0, 80), goal_amount: Math.max(0, parseInt(a) || 0) }).eq('family_id', state.family!.id).eq('user_id', state.kid!));
  };
  // порівняння місяців / рейтинг
  const mo = iso(state.now).slice(0, 7), pdt = new Date(state.now); pdt.setDate(1); pdt.setMonth(pdt.getMonth() - 1);
  const pm = iso(pdt).slice(0, 7);
  const earnedIn = (cid: string, m: string) => {
    const a = state.grades.filter((g) => g.child_id === cid && g.status === 'approved');
    const au = autoRewards(a, state.challenges.filter((c) => c.child_id === cid), state.streak);
    return [...a.map((g) => [g.date, gradeValue(g, state.rates)] as const), ...state.ledger.filter((l) => l.child_id === cid && l.kind === 'bonus').map((l) => [l.date, l.amount] as const), ...au.map((x) => [x.date, x.amount] as const)]
      .reduce((t, [dt, v]) => (dt.startsWith(m) ? t + v : t), 0);
  };
  const avg = (m: string) => { const a = d.A.filter((g) => g.date.startsWith(m)); return a.length ? a.reduce((t, g) => t + g.grade, 0) / a.length : 0; };
  const kids = state.members.filter((x) => x.role === 'child');
  const arrow = (x: number, y: number) => (x > y ? html`<span class="pos" aria-label="краще">▲</span>` : x < y ? html`<span class="neg" aria-label="гірше">▼</span>` : nothing);
  let rank: TemplateResult;
  if (kids.length > 1) {
    const r = kids.map((c) => [c, earnedIn(c.user_id, mo)] as const).sort((a, b) => b[1] - a[1]);
    rank = html`<h3 class="h5"><span>${emo('🏆')} Рейтинг місяця</span></h3><ol class="rank">${r.map(([c, v], i) => html`<li class=${classMap({ me: c.user_id === state.kid })}><span>${emo(['🥇', '🥈', '🥉'][i] ?? '·')} ${c.name || 'Дитина'}</span><span>${uah(v)}</span></li>`)}</ol>`;
  } else {
    const a1 = avg(mo), a0 = avg(pm), e1 = state.kid ? earnedIn(state.kid, mo) : 0, e0 = state.kid ? earnedIn(state.kid, pm) : 0;
    rank = html`<h3 class="h5"><span>${emo('🏆')} Я проти минулого місяця</span></h3>
      <div class="kv"><span>Середній бал</span><b>${a1 ? a1.toFixed(1) : '—'} ${arrow(a1, a0)} <small>(${a0 ? a0.toFixed(1) : '—'})</small></b></div>
      <div class="kv"><span>Заробив</span><b>${uah(e1)} ${arrow(e1, e0)} <small>(${uah(e0)})</small></b></div>`;
  }
  const s = [...d.A].sort(byTime);
  return html`<div class="cards2">
      <section class="mini" aria-label="Ціль"><h3 class="h5"><span>${emo('🎯')} Ціль</span>${state.kid ? html`<button type="button" @click=${editGoal}>змінити</button>` : nothing}</h3>
        ${ga ? html`<div class="big2">${k?.goal_title || 'Мрія'}</div><div class="msg">${bal} з ${ga} ₴ · ${gp >= 100 ? emo('🎉 можна купувати!') : `ще ${ga - bal} ₴`}</div><div class="bar"><i style=${styleMap({ width: gp + '%' })}></i></div>`
          : html`<div class="msg">Постав ціль — на що збираєш?</div>`}</section>
      <section class="mini" id="streak" aria-label="Серія"><h3 class="h5"><span>${emo('🔥')} Серія</span></h3><div class="big2">${d.run} поспіль</div>
        <div class="msg">оцінки ≥ ${state.streak.min}: ще ${state.streak.len - d.run} до бонусу +${state.streak.bonus} ₴</div><div class="bar"><i style=${styleMap({ width: (d.run / state.streak.len) * 100 + '%' })}></i></div></section>
      <section class="mini" aria-label="Рейтинг">${rank}</section>
    </div>
    ${d.CH.length ? html`<section class="mini" style="margin-top:10px" aria-label="Челенджі"><h3 class="h5"><span>${emo('🏅')} Челенджі</span></h3>${d.CH.map((c) => {
      const h = challengeHits(c, s).length, done = h >= c.need, over = d.today > c.end_date;
      return html`<div class=${classMap({ ch: true, done: done || over })}><span><b>${c.title}</b>
        <small>${c.subject ? html`${emo(subjEmoji(c.subject))} ${c.subject} · ` : nothing}оцінка ≥ ${c.min_grade} · ${Math.min(h, c.need)}/${c.need} · ${done ? emo('✅ виконано!') : over ? emo('⌛ час вийшов') : 'до ' + fmtDay(c.end_date)}</small>
        <div class="bar"><i style=${styleMap({ width: Math.min(100, (h / c.need) * 100) + '%', background: done ? '#00866b' : 'var(--accent)' })}></i></div></span>
        <span><b class="pos">+${c.reward} ₴</b>${state.role === 'parent' ? html` <button type="button" class="x" aria-label="Видалити челендж" @click=${() => del('school_challenges', c.id, 'Видалити челендж?')}>✕</button>` : nothing}</span></div>`;
    })}</section>` : nothing}`;
}

function gradeInput(): TemplateResult {
  const f = state.form;
  return html`<div class="form">
      <button type="button" class="pick" aria-haspopup="listbox" aria-label="Предмет" @click=${(e: Event) => pickSubject(e, f.subject, (s) => set((st) => { st.form.subject = s; }))}>${subjLabel(f.subject)}</button>
      <button type="button" class="pick" aria-haspopup="dialog" aria-label="Дата оцінки" @click=${(e: Event) => pickDate(e, f.date, (v) => set((st) => { st.form.date = v; }), [['Вчора', iso(addDays(new Date(), -1))], ['Сьогодні', iso(new Date())]])}>${dateLabel(f.date)}</button>
    </div>
    <div class="gbtn" role="group" aria-label="Поставити оцінку" style=${styleMap({ opacity: state.kid ? '1' : '.4' })}>${Array.from({ length: 12 }, (_, i) => 12 - i).map((g) =>
      html`<button type="button" ?disabled=${!state.kid} style=${styleMap({ background: gradeColor(g) })} aria-label=${`Оцінка ${g}, ${uah(state.rates[g] ?? 0)}`} @click=${(e: MouseEvent) => addGrade(g, e)}>${g}</button>`)}</div>`;
}

function homework(d: D): TemplateResult {
  const f = state.form, h = state.homework.filter((x) => x.child_id === state.kid);
  const todo = h.filter((x) => !x.done).sort((a, b) => (a.due < b.due ? -1 : 1));
  const done = h.filter((x) => x.done).sort((a, b) => (a.due < b.due ? 1 : -1)).slice(0, 5);
  const setSubj = (s: string) => set((st) => { st.form.hwSubject = s; st.form.hwDate = nextLessonDate(s, new Date()); });
  const add = async (e: Event) => {
    e.preventDefault();
    const inp = document.getElementById('hText') as HTMLInputElement;
    if (!state.kid || !inp.value.trim()) return;
    await run(sb.from('school_homework').insert({ family_id: state.family!.id, child_id: state.kid, subject: f.hwSubject, due: f.hwDate, text: inp.value.trim() }), () => { inp.value = ''; });
  };
  const toggle = async (x: (typeof h)[number], e: MouseEvent) => {
    const v = !x.done;
    set(() => { x.done = v; });
    if (v) boom(e.clientX, e.clientY, 40);
    const { error } = await sb.from('school_homework').update({ done: v }).eq('id', x.id);
    if (error) { set(() => { x.done = !v; }); void alert(errText(error)); }
  };
  return html`<h3 class="h4 gap">${emo('📝')} Домашні завдання</h3>
    <form class="form" @submit=${add}>
      <button type="button" class="pick" aria-haspopup="listbox" aria-label="Предмет домашки" @click=${(e: Event) => pickSubject(e, f.hwSubject, setSubj)}>${subjLabel(f.hwSubject)}</button>
      <button type="button" class="pick" aria-haspopup="dialog" aria-label="На коли" @click=${(e: Event) => pickDate(e, f.hwDate, (v) => set((st) => { st.form.hwDate = v; }), [['Сьогодні', iso(new Date())], ['Завтра', iso(addDays(new Date(), 1))]])}>${dateLabel(f.hwDate)}</button>
      <label class="sr-only" for="hText">Що задали</label><input id="hText" class="grow" placeholder="Що задали?" maxlength="500" required>
      <button>Додати</button>
    </form>
    <ul id="hwl" class="hwl">${todo.length || done.length ? [...todo, ...done].map((x) => html`<li class=${classMap({ hw: true, dn: x.done, late: !x.done && x.due < d.today })}>
        <button type="button" role="checkbox" aria-checked=${x.done} aria-label=${`Виконано: ${x.subject}`} class=${classMap({ cb: true, on: x.done })} @click=${(e: MouseEvent) => toggle(x, e)}></button>
        <span class="t2">${emo(subjEmoji(x.subject))} <b>${x.subject}</b> — ${x.text}<small>${!x.done && x.due < d.today ? emo('⚠️ протерміновано · ') : nothing}на ${fmtDay(x.due)}</small></span>
        <button type="button" class="x" aria-label="Видалити завдання" @click=${() => del('school_homework', x.id, 'Видалити завдання?')}>✕</button></li>`)
      : html`<li class="msg">${emo('Домашки немає 🎉')}</li>`}</ul>`;
}

function chart(d: D): TemplateResult {
  const by = new Map<string, number[]>();
  for (const g of d.A) by.set(g.subject, [...(by.get(g.subject) ?? []), g.grade]);
  const rows = [...by].map(([k, v]) => [k, v.reduce((a, b) => a + b, 0) / v.length, v.length] as const).sort((a, b) => b[1] - a[1]);
  return html`<h3 class="h4 gap">${emo('📈')} Середній бал за предметами</h3>
    ${rows.length ? html`<table class="chart"><caption class="sr-only">Середній бал за предметами (шкала 1–12)</caption>
      <thead class="sr-only"><tr><th>Предмет</th><th>Графік</th><th>Середній бал</th></tr></thead>
      <tbody>${rows.map(([k, a, n]) => html`<tr title=${`${k}: ${a.toFixed(1)} (оцінок: ${n})`}><th scope="row">${emo(subjEmoji(k))} ${k}</th>
        <td aria-hidden="true"><span class="tr"><i style=${styleMap({ width: (a / 12) * 100 + '%', background: gradeColor(Math.round(a)) })}></i></span></td><td><b>${a.toFixed(1)}</b></td></tr>`)}</tbody></table>`
      : html`<p class="msg">Графік з’явиться після перших підтверджених оцінок.</p>`}`;
}

function parentTools(d: D): TemplateResult | typeof nothing {
  if (state.role !== 'parent') return nothing;
  const f = state.form;
  const val = (id: string) => (document.getElementById(id) as HTMLInputElement).value;
  const bonus = async (e: Event) => {
    e.preventDefault();
    const n = parseInt(val('bAmt'));
    if (!n || !state.kid) return;
    await run(sb.from('school_ledger').insert({ family_id: state.family!.id, child_id: state.kid, kind: 'bonus', amount: n, note: val('bNote').trim() }), () => {
      (e.target as HTMLFormElement).reset();
      if (n > 0) boom();
    });
  };
  const pay = async () => {
    const v = await prompt('Скільки виплачено, ₴?', d.t.balance);
    const n = parseInt(v ?? '');
    if (!(n > 0) || !state.kid) return;
    await run(sb.from('school_ledger').insert({ family_id: state.family!.id, child_id: state.kid, kind: 'payout', amount: n, note: 'Виплачено' }), () => boom());
  };
  const chal = async (e: Event) => {
    e.preventDefault();
    if (!state.kid) return void alert('Спершу додайте дитину');
    const n = (id: string, d0: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, parseInt(val(id)) || d0));
    const days = n('cDays', 7, 1, 90);
    await run(sb.from('school_challenges').insert({
      family_id: state.family!.id, child_id: state.kid, title: val('cTitle').trim(), subject: f.chSubject || null,
      min_grade: n('cMin', 10, 1, 12), need: n('cNeed', 3, 1, 50), reward: n('cRew', 100, 0, 100000), end_date: iso(addDays(new Date(), days - 1)),
    }), () => { (document.getElementById('cTitle') as HTMLInputElement).value = ''; boom(); });
  };
  const saveRates = async () => {
    const r: Record<string, number> = { streak_len: parseInt(val('stLen')) || 5, streak_min: parseInt(val('stMin')) || 7, streak_bonus: Math.max(0, parseInt(val('stBon')) || 0) };
    document.querySelectorAll<HTMLInputElement>('[data-rate]').forEach((i) => { r[i.dataset.rate!] = parseInt(i.value) || 0; });
    if (await run(sb.from('school_families').update({ rates: r }).eq('id', state.family!.id))) void alert('Збережено ✓ Новий курс діє для нових підтверджень.');
  };
  const copy = (t: string) => navigator.clipboard?.writeText(t).then(() => alert('Скопійовано: ' + t), () => undefined);
  return html`<h3 class="h4 gap">${emo('🎁')} Бонус / штраф / виплата</h3>
    <form class="form" @submit=${bonus}>
      <label class="sr-only" for="bAmt">Сума</label><input type="number" id="bAmt" inputmode="numeric" placeholder="сума ₴ (мінус = штраф)" required class="w190">
      <label class="sr-only" for="bNote">За що</label><input id="bNote" placeholder="за що" maxlength="200">
      <button>${emo('🎁 Нарахувати')}</button>
      ${d.t.balance > 0 ? html`<button type="button" class="paybtn" @click=${pay}>${emo('💸 Виплатити')}</button>` : nothing}
    </form>
    <h3 class="h4 gap">${emo('🏅')} Новий челендж</h3>
    <form class="form" @submit=${chal}>
      <label class="sr-only" for="cTitle">Назва</label><input id="cTitle" class="grow" placeholder="Напр.: 3 десятки з математики" maxlength="120" required>
      <button type="button" class="pick" aria-haspopup="listbox" aria-label="Предмет челенджу" @click=${(e: Event) => pickSubject(e, f.chSubject, (s) => set((st) => { st.form.chSubject = s; }), true)}>${subjLabel(f.chSubject, true)}</button>
      <label>оцінка ≥ <input type="number" id="cMin" value="10" min="1" max="12" class="w64"></label>
      <label>разів <input type="number" id="cNeed" value="3" min="1" max="50" class="w64"></label>
      <label>днів <input type="number" id="cDays" value="7" min="1" max="90" class="w64"></label>
      <label>нагорода ₴ <input type="number" id="cRew" value="100" min="0" class="w90"></label>
      <button>${emo('🏅 Створити')}</button>
    </form>
    <details class="gap"><summary>${emo('⚙️ Курс оцінок і запрошення')}</summary>
      <div class="rgrid">${Array.from({ length: 12 }, (_, i) => 12 - i).map((g) => html`<label><span class="n" style=${styleMap({ background: gradeColor(g) })}>${g}</span><span class="sr-only">Оцінка ${g}, гривень</span><input type="number" data-rate=${g} .value=${live(String(state.rates[g] ?? 0))}></label>`)}</div>
      <div class="srow">${emo('🔥')} Серія: кожні <input type="number" id="stLen" min="2" max="30" aria-label="Кількість оцінок" .value=${live(String(state.streak.len))}> оцінок поспіль ≥ <input type="number" id="stMin" min="1" max="12" aria-label="Мінімальна оцінка" .value=${live(String(state.streak.min))}> дають бонус <input type="number" id="stBon" min="0" class="w84" aria-label="Бонус у гривнях" .value=${live(String(state.streak.bonus))}> ₴</div>
      <button type="button" @click=${saveRates}>Зберегти курс</button>
      <p class="gap">Код для дитини: <button type="button" class="code" title="Скопіювати" @click=${() => copy(state.family!.invite_code)}>${state.family!.invite_code}</button>
        · для другого з батьків: <button type="button" class="code" title="Скопіювати" @click=${() => copy(state.parentCode)}>${state.parentCode}</button></p>
    </details>`;
}

function history(d: D): TemplateResult {
  const par = state.role === 'parent';
  type Row = { key: string; date: string; created_at: string; t: TemplateResult };
  const rows: Row[] = [
    ...d.G.map((x): Row => ({ key: x.id, date: x.date, created_at: x.created_at, t: x.status === 'pending'
      ? html`<li class="gi wait"><span class="g" style=${styleMap({ background: gradeColor(x.grade) })}>${x.grade}</span><span>${emo(subjEmoji(x.subject))} ${x.subject}<small>${fmtDay(x.date)} · ${emo('⏳')} чекає підтвердження батьків</small></span><b class="muted">${uah(state.rates[x.grade] ?? 0)}</b>
          ${par ? html`<span class="acts2"><button type="button" class="ok" aria-label="Підтвердити" @click=${(e: MouseEvent) => approve(x, e)}>✓</button><button type="button" class="x" aria-label="Відхилити" @click=${() => del('school_grades', x.id)}>✕</button></span>`
            : html`<button type="button" class="x" aria-label="Видалити" @click=${() => del('school_grades', x.id)}>✕</button>`}</li>`
      : html`<li class="gi"><span class="g" style=${styleMap({ background: gradeColor(x.grade) })}>${x.grade}</span><span>${emo(subjEmoji(x.subject))} ${x.subject}<small>${fmtDay(x.date)} · ${emo('✅')} підтверджено</small></span><b class=${tone(gradeValue(x, state.rates))}>${uah(gradeValue(x, state.rates))}</b>
          ${par ? html`<button type="button" class="x" aria-label="Скасувати підтвердження" @click=${() => unapprove(x)}>↺</button>` : html`<span></span>`}</li>` })),
    ...d.L.map((x): Row => {
      const b = x.kind === 'bonus';
      return { key: x.id, date: x.date, created_at: x.created_at, t: html`<li class="gi"><span class=${classMap({ g: true, gb: b, gp: !b })}>${emo(b ? (x.amount >= 0 ? '🎁' : '⚠️') : '💸')}</span><span>${x.note || (b ? 'Бонус' : 'Виплачено')}<small>${fmtDay(x.date)}</small></span><b class=${b ? tone(x.amount) : ''}>${b ? uah(x.amount) : '−' + x.amount + ' ₴'}</b>
        ${par ? html`<button type="button" class="x" aria-label="Видалити" @click=${() => del('school_ledger', x.id)}>✕</button>` : html`<span></span>`}</li>` };
    }),
    ...d.auto.map((x): Row => ({ key: x.id, date: x.date, created_at: x.created_at, t: html`<li class="gi"><span class="g gv">${emo(x.icon)}</span><span>${x.note}<small>${fmtDay(x.date)} · нараховано автоматично</small></span><b class="pos">${uah(x.amount)}</b><span></span></li>` })),
  ].sort((a, b) => -byTime(a, b)).slice(0, 60);
  return html`<ul class="gl" id="gl" aria-label="Історія" tabindex="0">${rows.length ? rows.map((r) => r.t) : html`<li class="msg">${emo('Ще немає оцінок — натисни на цифру вище 👆')}</li>`}</ul>
    <p class="rates">Курс: ${Object.entries(state.rates).sort((a, b) => Number(b[0]) - Number(a[0])).map(([g, v]) => `${g} → ${uah(v)}`).join(' · ')}</p>`;
}

function mainView(): TemplateResult {
  const d = derive(), par = state.role === 'parent';
  const kids = state.members.filter((m) => m.role === 'child');
  return html`${par ? html`<div class="tabs" role="tablist" aria-label="Діти">${kids.length ? kids.map((k) =>
      html`<button type="button" role="tab" aria-selected=${k.user_id === state.kid} class=${classMap({ on: k.user_id === state.kid })} @click=${() => set({ kid: k.user_id })}>${emo(k.avatar || '🧒')} ${k.name || 'Дитина'}</button>`)
      : html`<p class="msg">Дітей ще немає — дай дитині код запрошення з розділу «Курс оцінок і запрошення».</p>`}</div>` : nothing}
    ${summary(d)}${cards(d)}
    <h3 class="h4 gap">${emo('➕')} Додати оцінку ${d.pend.length ? html`<span class="pend">· ${emo('⏳')} ${d.pend.length} чекає підтвердження</span>` : nothing}</h3>
    ${gradeInput()}${homework(d)}${chart(d)}${parentTools(d)}${history(d)}
    <div class="acts">
      <button type="button" @click=${togglePush}>${emo(state.pushOn ? '🔕 Вимкнути сповіщення' : '🔔 Сповіщення на телефон')}</button>
      ${state.canInstall ? html`<button type="button" @click=${installApp}>${emo('📱 Встановити застосунок')}</button>` : nothing}
      <button type="button" @click=${() => void signOut()}>Вийти</button>
    </div>
    ${state.pushMsg ? html`<p class=${classMap({ msg: true, pos: !!state.pushMsg.ok, neg: !state.pushMsg.ok })} role="status">${emo(state.pushMsg.text)}</p>` : nothing}`;
}

export function gradesSection(): TemplateResult {
  const m = me();
  const body = state.view === 'auth' ? authView() : state.view === 'join' ? joinView() : state.view === 'main' ? mainView()
    : state.view === 'offline' ? html`<p class="msg neg" role="alert">${state.authMsg?.text ?? 'Немає зʼєднання'}</p>` : html`<p class="msg" role="status">Завантаження…</p>`;
  return html`<section class="side gr" id="grades" aria-labelledby="gr-h">
    <div class="gr-h"><h2 class="h4" id="gr-h">${emo('🏆')} Оцінки та нагороди</h2>
      ${state.view === 'main' && state.me ? html`<span class="who"><button type="button" class="av" aria-label="Аватар, колір і стиль" @click=${avatarPop}>${emo(m?.avatar || (state.role === 'parent' ? '👨‍👩‍👦' : '🧒'))}</button>${m?.name || state.me.email} · ${state.family?.name}</span>` : nothing}</div>
    ${body}
  </section>`;
}
