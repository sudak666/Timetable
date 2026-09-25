import { html } from 'lit-html';
import { classMap } from 'lit-html/directives/class-map.js';
import { SUBJECT_LIST, subjEmoji } from '../data/schedule';
import { emo } from '../lib/emoji';
import { addDays, fmtDay, iso } from '../lib/time';
import { closePop, openPop } from './popover';

const MONTHS = ['січень', 'лютий', 'березень', 'квітень', 'травень', 'червень', 'липень', 'серпень', 'вересень', 'жовтень', 'листопад', 'грудень'];
const WD = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Нд'];

export const subjLabel = (s: string, any = false) => (s ? emo(`${subjEmoji(s)} ${s}`) : any ? emo('🌐 Будь-який предмет') : '');

export function dateLabel(v: string): unknown {
  const t = new Date(), today = iso(t), y = iso(addDays(t, -1)), tm = iso(addDays(t, 1));
  return emo('📅 ' + (v === today ? 'Сьогодні' : v === y ? 'Вчора' : v === tm ? 'Завтра' : fmtDay(v)));
}

export function pickSubject(e: Event, cur: string, cb: (s: string) => void, any = false): void {
  const pick = (s: string) => { cb(s); closePop(); };
  openPop(e.currentTarget as HTMLElement, () => html`<div class="plist" role="listbox" aria-label="Предмет">
    ${any ? html`<button type="button" role="option" aria-selected=${!cur} class=${classMap({ opt: true, on: !cur })} @click=${() => pick('')}>${emo('🌐 Будь-який предмет')}</button>` : null}
    ${SUBJECT_LIST.map((s) => html`<button type="button" role="option" aria-selected=${s === cur} class=${classMap({ opt: true, on: s === cur })} @click=${() => pick(s)}>${emo(subjEmoji(s))} ${s}</button>`)}
  </div>`, 'Вибір предмета');
}

export function pickDate(e: Event, sel: string, cb: (d: string) => void, quick: [string, string][]): void {
  const view = new Date(sel + 'T12:00');
  view.setDate(1);
  const pick = (d: string) => { cb(d); closePop(); };
  openPop(e.currentTarget as HTMLElement, (redraw) => {
    const y = view.getFullYear(), m = view.getMonth(), first = (new Date(y, m, 1).getDay() + 6) % 7, today = iso(new Date());
    const days = Array.from({ length: 42 }, (_, i) => new Date(y, m, 1 - first + i, 12));
    const nav = (n: number) => { view.setMonth(view.getMonth() + n); redraw(); };
    return html`<div class="cal">
      <div class="hd"><button type="button" aria-label="Попередній місяць" @click=${() => nav(-1)}>‹</button><span aria-live="polite">${MONTHS[m]} ${y}</span><button type="button" aria-label="Наступний місяць" @click=${() => nav(1)}>›</button></div>
      <div class="g" role="grid">${WD.map((w) => html`<span role="columnheader">${w}</span>`)}${days.map((d) => {
        const k = iso(d);
        return html`<button type="button" role="gridcell" aria-selected=${k === sel} aria-label=${fmtDay(k)} class=${classMap({ o: d.getMonth() !== m, t: k === today, s: k === sel })} @click=${() => pick(k)}>${d.getDate()}</button>`;
      })}</div>
      <div class="ft">${quick.map(([l, d]) => html`<button type="button" @click=${() => pick(d)}>${l}</button>`)}</div>
    </div>`;
  }, 'Вибір дати');
}
