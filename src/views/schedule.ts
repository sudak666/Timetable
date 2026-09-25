import { html, nothing, type TemplateResult } from 'lit-html';
import { classMap } from 'lit-html/directives/class-map.js';
import { styleMap } from 'lit-html/directives/style-map.js';
import { BELLS, RANGES, SUBJECTS, WEEK, bellEnd, bellStart, isSubject } from '../data/schedule';
import { emo, emojiSrc } from '../lib/emoji';
import { addDays, clockOf, hms, iso, mondayOf, nextSchoolDay, statusAt } from '../lib/time';
import { set, state, store } from '../state';
import { boom } from '../ui/confetti';
import { alert } from '../ui/dialog';

const subjColor = (s: string) => (isSubject(s) ? SUBJECTS[s].color : '#4b5563');
const subjEmoji = (s: string) => (isSubject(s) ? SUBJECTS[s].emoji : '');

export function nowCard(): TemplateResult {
  const c = clockOf(state.now), s = statusAt(c);
  let body: TemplateResult, subject = '', brk = false;
  if (s.kind === 'lesson') {
    const cur = s.lessons[s.idx]!.subject, nx = s.lessons[s.idx + 1]?.subject;
    subject = cur;
    body = html`<small>Зараз · ${s.idx + 1} урок з ${s.lessons.length}</small><h2>${cur}</h2>
      <div class="big" role="timer" aria-label="До дзвінка">${hms(s.end - c.min)}</div>
      <div>до дзвінка${nx ? ` · далі ${nx}` : ' · останній урок'}</div>
      <div class="bar" role="progressbar" aria-label="Прогрес уроку" aria-valuemin="0" aria-valuemax="100" aria-valuenow=${Math.round(((c.min - s.start) / (s.end - s.start)) * 100)}><i style=${styleMap({ width: ((c.min - s.start) / (s.end - s.start)) * 100 + '%' })}></i></div>`;
  } else if (s.kind === 'break') {
    brk = true;
    subject = s.lessons[s.idx]!.subject;
    body = html`<small>${emo('☕')} Перерва ${s.start - s.prevEnd} хв</small><h2>Далі: ${subject}</h2>
      <div class="big" role="timer">${hms(s.start - c.min)}</div><div>до ${s.idx + 1} уроку о ${bellStart(s.idx)}</div>
      <div class="bar"><i style=${styleMap({ width: ((c.min - s.prevEnd) / (s.start - s.prevEnd)) * 100 + '%' })}></i></div>`;
  } else if (s.kind === 'before') {
    subject = s.lessons[0]!.subject;
    body = html`<small>Сьогодні ${s.lessons.length} уроків</small><h2>Перший: ${subject}</h2><div class="big" role="timer">${hms(s.start - c.min)}</div><div>до початку о ${bellStart(0)}</div>`;
  } else {
    const n = nextSchoolDay(c.weekday), d = WEEK[n]!;
    body = html`<small>Уроки закінчились ${emo('🎉')}</small><h2>Далі: ${d.name}</h2><div>Перший урок — ${d.lessons[0]!.subject} о ${bellStart(0)}</div>`;
  }
  const src = emojiSrc(subject ? subjEmoji(subject) : '🎉');
  return html`<div class="nowwrap">
    <section class=${classMap({ now: true, brk })} aria-live="polite" @click=${(e: MouseEvent) => boom(e.clientX, e.clientY, 60)}>${body}</section>
    ${src ? html`<img class="em" src=${src} alt="" decoding="async">` : nothing}
  </div>`;
}

export function sidePanel(): TemplateResult {
  const c = clockOf(state.now), s = statusAt(c);
  const target = s.kind === 'done' || !WEEK[c.weekday] ? nextSchoolDay(c.weekday) : c.weekday;
  const day = WEEK[target]!;
  const bag = [...new Set(day.lessons.map((l) => l.subject))];
  const lessons = WEEK[c.weekday]?.lessons;
  let prog: TemplateResult;
  if (lessons) {
    const a = RANGES[0]![0], b = RANGES[lessons.length - 1]![1], p = Math.min(1, Math.max(0, (c.min - a) / (b - a)));
    prog = html`Навчальний день: ${Math.round(p * 100)}%<div class="bar"><i style=${styleMap({ width: p * 100 + '%' })}></i></div>`;
  } else prog = html`Вихідний ${emo('😎')}`;
  return html`<aside class="side">
    <h2 class="h4">${target === c.weekday ? 'Рюкзак на сьогодні' : 'Рюкзак на ' + day.name.toLowerCase()}</h2>
    <ul class="chips">${bag.map((x) => html`<li class="chip" style=${styleMap({ background: subjColor(x) })}>${x}</li>`)}</ul>
    <div class="dayprog">${prog}</div>
  </aside>`;
}

function weekendText(): TemplateResult {
  const c = clockOf(state.now);
  if (c.weekday > 4) return html`Ура, вихідні! Відпочивай ${emo('🎉')}`;
  const left = (4 - c.weekday) * 1440 + (RANGES[WEEK[4]!.lessons.length - 1]![1] - c.min);
  return left <= 0 ? html`Вихідні вже тут! ${emo('🥳')}` : html`Ще <b>${Math.floor(left / 1440)} д ${Math.floor((left % 1440) / 60)} год</b> — і вихідні! ${emo('🏖️')}`;
}

const FACTS = ['Восьминіг має три серця і блакитну кров 🐙', 'Мед ніколи не псується — його знаходили в єгипетських пірамідах 🍯', 'Равлик може спати до 3 років 🐌', 'У космосі неможливо заплакати — сльози не падають 🚀', 'Банани — це ягоди, а полуниця — ні 🍌', 'Серце кита розміром з автомобіль 🐋', 'Блискавка гарячіша за поверхню Сонця ⚡', 'Мурахи ніколи не сплять по-справжньому 🐜', 'Жирафи сплять лише 30 хвилин на добу 🦒', 'На Венері день довший за рік 🪐', 'Коти сплять близько 16 годин на день 🐱', 'Дельфіни дають одне одному імена 🐬', 'Метелики відчувають смак ногами 🦋', 'В Україні понад 70 000 річок 🌊'];

export function addStar(x?: number, y?: number): void {
  const n = Math.min(10, state.stars + 1);
  store.set('stars', JSON.stringify({ d: iso(new Date()), n }));
  set({ stars: n });
  boom(x, y, 30);
}

export function funCards(quiz: TemplateResult): TemplateResult {
  return html`<div class="fun">
    <section class="side quiz" aria-labelledby="qz-h"><h2 class="h4" id="qz-h">${emo('🧠')} Вікторина на перерві</h2>${quiz}</section>
    <section class="side" aria-labelledby="fact-h"><h2 class="h4" id="fact-h">${emo('🦉')} Цікавий факт</h2>
      <p aria-live="polite">${emo(FACTS[state.factIdx % FACTS.length]!)}</p>
      <div class="row"><span></span><button type="button" @click=${() => set({ factIdx: state.factIdx + 1 })}>Ще факт</button></div></section>
    <section class="side" aria-labelledby="wk-h"><h2 class="h4" id="wk-h">${emo('🌈')} До вихідних</h2><p>${weekendText()}</p>
      <h2 class="h4" style="margin-top:12px">${emo('⭐')} Мої зірочки за сьогодні</h2>
      <div class="row"><span class="stars" aria-label=${`Зірочок: ${state.stars}`}>${state.stars ? emo('⭐'.repeat(state.stars)) : '—'}</span>
        <button type="button" @click=${(e: MouseEvent) => addStar(e.clientX, e.clientY)}>+ зірка</button></div></section>
  </div>`;
}

/** Невиконана домашка на цей тиждень: 'YYYY-MM-DD|предмет'. */
function hwDueSet(): Set<string> {
  return new Set(state.homework.filter((h) => h.child_id === state.kid && !h.done).map((h) => h.due + '|' + h.subject));
}

export function weekGrid(): TemplateResult {
  const c = clockOf(state.now), mon = mondayOf(state.now), due = hwDueSet();
  const tabs = ['Весь тиждень', ...WEEK.map((d) => d.name)];
  return html`
    <nav class="tabs" aria-label="Дні тижня" role="tablist">${tabs.map((t, i) => {
      const v = i - 1 < 0 ? null : i - 1;
      return html`<button type="button" role="tab" aria-selected=${state.day === v} class=${classMap({ on: state.day === v })} @click=${() => set({ day: v })}>${t}</button>`;
    })}</nav>
    <div class=${classMap({ grid: true, hl: !!state.highlight })}>${WEEK.map((d, i) => {
      if (state.day !== null && state.day !== i) return nothing;
      const today = i === c.weekday, date = iso(addDays(mon, i));
      const st = (a: number, b: number) => (today ? (c.min >= b ? 'past' : c.min >= a ? 'cur' : '') : '');
      return html`<section class=${classMap({ day: true, today })} aria-label=${d.name}>
        <h3><span>${d.name}${today ? html`<span class="badge">Сьогодні</span>` : nothing}</span><span class="end">${d.lessons.length} ур. · до ${bellEnd(d.lessons.length - 1)}</span></h3>
        <ol class="lessons">${d.lessons.map((l, j) => {
          const [a, b] = RANGES[j]!, nx = RANGES[j + 1], k = st(a, b);
          return html`<li class=${classMap({ l: true, [k]: !!k, match: state.highlight === l.subject })} aria-current=${k === 'cur' ? 'time' : nothing}>
              <span class="n" style=${styleMap({ background: subjColor(l.subject) })}>${j + 1}</span>
              <span class="t">${BELLS[j]!.replace('-', ' – ')}</span>
              <span class="s">${emo(subjEmoji(l.subject))} ${l.subject}${l.optional ? html` <span class="opt">факультатив</span>` : nothing}${due.has(date + '|' + l.subject) ? html`<span class="hwdot" title="Є домашка">ДЗ</span>` : nothing}</span>
            </li>${j < d.lessons.length - 1 && nx ? html`<li class=${classMap({ b: true, long: nx[0] - b >= 20, [st(b, nx[0])]: !!st(b, nx[0]) })} aria-label=${`Перерва ${nx[0] - b} хвилин`}>${emo('☕')} перерва ${nx[0] - b} хв${nx[0] - b >= 20 ? ' · велика' : ''}</li>` : nothing}`;
        })}</ol>
      </section>`;
    })}</div>`;
}

export function weekStats(): TemplateResult {
  const cnt = new Map<string, number>();
  WEEK.forEach((d) => d.lessons.forEach((l) => cnt.set(l.subject, (cnt.get(l.subject) ?? 0) + 1)));
  const rows = [...cnt].sort((a, b) => b[1] - a[1]);
  return html`<section class="side st-sec" aria-labelledby="st-h">
    <h2 class="h4" id="st-h">Уроків на тиждень · натисни, щоб підсвітити</h2>
    <div class="chips">${rows.map(([k, v]) => html`<button type="button" class="chip" aria-pressed=${state.highlight === k}
      style=${styleMap({ background: subjColor(k), opacity: state.highlight && state.highlight !== k ? '.4' : '1' })}
      @click=${() => set({ highlight: state.highlight === k ? null : k })}>${k}<b>×${v}</b></button>`)}</div>
  </section>`;
}

export async function toggleLessonAlerts(): Promise<void> {
  if (typeof Notification === 'undefined') return void alert('Браузер не підтримує сповіщення');
  if (!state.lessonAlerts && Notification.permission !== 'granted' && (await Notification.requestPermission()) !== 'granted') return;
  const v = !state.lessonAlerts;
  store.set('notif', v ? '1' : '0');
  set({ lessonAlerts: v });
}

let lastAlert = '';
/** Локальне нагадування за 2 хв до уроку (поки сторінка відкрита). */
export function lessonAlertTick(): void {
  if (!state.lessonAlerts) return;
  const c = clockOf(state.now), s = statusAt(c);
  if ((s.kind === 'break' || s.kind === 'before') && s.start - c.min <= 2 && s.start - c.min > 0) {
    const key = c.weekday + '-' + s.idx;
    if (key === lastAlert) return;
    lastAlert = key;
    try { new Notification('Через 2 хв: ' + s.lessons[s.idx]!.subject, { body: `${s.idx + 1} урок о ${bellStart(s.idx)}`, icon: 'assets/icon-192.png' }); } catch { /* ignore */ }
  }
}
