/**
 * Редактор розкладу дитини у вебі: уроки по днях, дзвінки, канікули.
 * Логіка — features/timetable.ts (тести), тут лише відображення. Зберігається одразу на сервер
 * (school_timetables, upsert за child_id); RLS дозволяє це батькам і самій дитині.
 */
import { html, nothing, type TemplateResult } from 'lit-html';
import { classMap } from 'lit-html/directives/class-map.js';
import { live } from 'lit-html/directives/live.js';
import { errText, loadFamily, sb } from '../api';
import { DAY_NAMES, subjEmoji } from '../data/schedule';
import {
  MAX_HOLIDAYS, MAX_LESSONS, MAX_SUBJECT, TYPICAL_BELLS, addBell, addHoliday, addLesson, fromRow, inputTime,
  lessonsLostByLastBell, moveLesson, normTime, removeHoliday, removeLastBell, removeLesson, sameDraft, setBell, setDays, setHoliday,
  setLesson, toRow, validate, type Draft,
} from '../features/timetable';
import { emo } from '../lib/emoji';
import { addDays, iso } from '../lib/time';
import { set, state, type EditorTab } from '../state';
import { confirm, prompt } from '../ui/dialog';
import { pickSubject } from '../ui/pickers';
import { toast } from '../ui/toast';

/** Редагувати розклад можуть батьки та сама дитина — так само дозволяє RLS. */
export const canEditSchedule = (): boolean => !!state.kid && !!state.family && (state.role === 'parent' || state.kid === state.me?.id);

const kidName = (id: string) => state.members.find((m) => m.user_id === id)?.name || 'дитина';

export function openEditor(): void {
  if (!canEditSchedule()) return;
  const base = fromRow(state.timetables.find((t) => t.child_id === state.kid));
  set({ editor: { kid: state.kid!, base, draft: base, tab: 'lessons', day: 0, saving: false }, day: null });
  scrollTo({ top: 0 });
}

const dirty = (): boolean => !!state.editor && !sameDraft(state.editor.base, state.editor.draft);

/** Незбережені зміни не губляться випадковим закриттям вкладки. */
addEventListener('beforeunload', (e) => { if (dirty()) e.preventDefault(); });

const update = (f: (d: Draft) => Draft) => set((s) => { if (s.editor) s.editor = { ...s.editor, draft: f(s.editor.draft) }; });
const go = (patch: { tab?: EditorTab; day?: number }) => set((s) => { if (s.editor) s.editor = { ...s.editor, ...patch }; });

async function close(): Promise<void> {
  if (dirty() && !(await confirm('Скинути незбережені зміни розкладу?'))) return;
  set({ editor: null });
}

async function save(): Promise<void> {
  const ed = state.editor, fid = state.family?.id;
  if (!ed || !fid || ed.saving || validate(ed.draft).length) return;
  if (!navigator.onLine) return void toast('Немає інтернету — розклад збережеться, коли зʼявиться звʼязок. Поки що не закривай сторінку.', 'err');
  set((s) => { if (s.editor) s.editor = { ...s.editor, saving: true }; });
  // updated_at ставить тригер на сервері.
  const { error } = await sb.from('school_timetables').upsert(toRow(ed.draft, ed.kid, fid), { onConflict: 'child_id' });
  if (error) {
    set((s) => { if (s.editor) s.editor = { ...s.editor, saving: false }; });
    return void toast('Розклад не збережено: ' + errText(error), 'err');
  }
  await loadFamily().catch(() => undefined);
  set({ editor: null });
  toast('Розклад збережено ✓', 'ok');
}

/* ---------------- уроки ---------------- */

async function customSubject(cb: (s: string) => void, cur = ''): Promise<void> {
  const v = (await prompt('Назва предмета', cur, 'text'))?.trim();
  if (!v) return;
  if (v.length > MAX_SUBJECT) return void toast(`Назва задовга (максимум ${MAX_SUBJECT} символів)`, 'err');
  cb(v);
}

function lessonsTab(d: Draft, day: number): TemplateResult {
  const lessons = d.week[day] ?? [];
  const full = lessons.length >= d.bells.length;
  return html`
    <div class="ed-row">
      <span>Навчальних днів:</span>
      <div class="seg" role="group" aria-label="Навчальних днів на тиждень">${[5, 6, 7].map((n) => html`
        <button type="button" class=${classMap({ on: d.week.length === n })} aria-pressed=${d.week.length === n}
          @click=${() => void changeDays(n)}>${n}</button>`)}</div>
    </div>
    <nav class="tabs" role="tablist" aria-label="День тижня">${d.week.map((ls, i) => html`
      <button type="button" role="tab" aria-selected=${i === day} class=${classMap({ on: i === day })} @click=${() => go({ day: i })}>
        ${DAY_NAMES[i]} <small>${ls.length || '—'}</small></button>`)}</nav>
    ${lessons.length ? html`<ol class="ed-list">${lessons.map((l, i) => html`<li>
        <span class="n">${i + 1}</span>
        <span class="t">${d.bells[i] ? `${d.bells[i].start}–${d.bells[i].end}` : ''}</span>
        <button type="button" class="subj" aria-label=${`${i + 1} урок: ${l.s}. Змінити предмет`}
          @click=${(e: Event) => pickSubject(e, l.s, (s) => update((x) => setLesson(x, day, i, { s })))}>${emo(subjEmoji(l.s) || '📘')} ${l.s}</button>
        <label class="opt"><input type="checkbox" .checked=${live(l.o)} @change=${(e: Event) => update((x) => setLesson(x, day, i, { o: (e.target as HTMLInputElement).checked }))}> факультатив</label>
        <span class="acts">
          <button type="button" class="ic" aria-label=${`Перемістити ${i + 1} урок вище`} ?disabled=${i === 0} @click=${() => update((x) => moveLesson(x, day, i, -1))}>↑</button>
          <button type="button" class="ic" aria-label=${`Перемістити ${i + 1} урок нижче`} ?disabled=${i === lessons.length - 1} @click=${() => update((x) => moveLesson(x, day, i, 1))}>↓</button>
          <button type="button" class="ic danger" aria-label=${`Прибрати ${i + 1} урок`} @click=${() => update((x) => removeLesson(x, day, i))}>✕</button>
        </span>
      </li>`)}</ol>`
      : html`<p class="muted">${DAY_NAMES[day]}: уроків ще немає.</p>`}
    <div class="ed-row">
      <button type="button" ?disabled=${full} @click=${(e: Event) => pickSubject(e, '', (s) => update((x) => addLesson(x, day, s)))}>+ Урок</button>
      <button type="button" ?disabled=${full} @click=${() => void customSubject((s) => update((x) => addLesson(x, day, s)))}>+ Свій предмет</button>
      ${full ? html`<small class="muted">Уроків стільки ж, скільки дзвінків (${d.bells.length}) — додай дзвінок у вкладці «Дзвінки».</small>` : nothing}
    </div>`;
}

async function changeDays(n: number): Promise<void> {
  const ed = state.editor;
  if (!ed) return;
  const lost = ed.draft.week.slice(n).reduce((a, ls) => a + ls.length, 0);
  if (lost && !(await confirm(`Буде прибрано ${lost} ${lost === 1 ? 'урок' : 'уроків'} з днів після ${DAY_NAMES[n - 1]}. Продовжити?`))) return;
  update((x) => setDays(x, n));
  if (ed.day >= n) go({ day: n - 1 });
}

/* ---------------- дзвінки ---------------- */

function onTime(i: number, key: 'start' | 'end') {
  return (e: Event) => {
    const v = normTime((e.target as HTMLInputElement).value);
    update((x) => setBell(x, i, { [key]: v ?? '' }));
  };
}

async function dropLastBell(): Promise<void> {
  const ed = state.editor;
  if (!ed) return;
  const lost = lessonsLostByLastBell(ed.draft);
  if (lost && !(await confirm(`Останній урок зникне в ${lost} ${lost === 1 ? 'дні' : 'днях'}. Прибрати дзвінок?`))) return;
  update(removeLastBell);
}

async function typicalBells(): Promise<void> {
  if (!(await confirm('Поставити типові дзвінки (8:30, уроки по 45 хв)? Уроки лишаться, зміниться лише час.'))) return;
  update((x) => {
    const n = Math.max(TYPICAL_BELLS.length, ...x.week.map((ls) => ls.length));
    let y: Draft = { ...x, bells: TYPICAL_BELLS.map((b) => ({ ...b })) };
    while (y.bells.length < n) y = addBell(y);
    return y;
  });
}

function bellsTab(d: Draft): TemplateResult {
  return html`
    <ol class="ed-list">${d.bells.map((b, i) => {
      // Перерву показуємо лише між коректними дзвінками — інакше число вводить в оману.
      const ok = (x?: { start: string; end: string }) => !!x && !!normTime(x.start) && !!normTime(x.end) && mins(x.end) > mins(x.start);
      const next = d.bells[i + 1], gap = next && ok(b) && ok(next) ? mins(next.start) - mins(b.end) : null;
      return html`<li>
        <span class="n">${i + 1}</span>
        <input type="time" aria-label=${`${i + 1} урок: початок`} .value=${live(inputTime(b.start))} @change=${onTime(i, 'start')}>
        <span>–</span>
        <input type="time" aria-label=${`${i + 1} урок: кінець`} .value=${live(inputTime(b.end))} @change=${onTime(i, 'end')}>
        ${gap !== null && gap >= 0 ? html`<small class="muted">перерва ${gap} хв</small>` : nothing}
      </li>`;
    })}</ol>
    <div class="ed-row">
      <button type="button" ?disabled=${d.bells.length >= MAX_LESSONS} @click=${() => update(addBell)}>+ Дзвінок</button>
      <button type="button" ?disabled=${!d.bells.length} @click=${() => void dropLastBell()}>− Останній</button>
      <button type="button" @click=${() => void typicalBells()}>Типові дзвінки</button>
    </div>`;
}

const mins = (t: string) => {
  const [h = '0', m = '0'] = t.split(':');
  return Number(h) * 60 + Number(m);
};

/* ---------------- канікули ---------------- */

function holidaysTab(d: Draft): TemplateResult {
  const today = iso(new Date());
  return html`
    ${d.holidays.length ? html`<ol class="ed-list">${d.holidays.map(([a, b], i) => html`<li>
        <span class="hol">
          <input type="date" aria-label=${`Канікули ${i + 1}: перший день`} .value=${live(a)} @change=${(e: Event) => update((x) => setHoliday(x, i, (e.target as HTMLInputElement).value, b))}>
          <span aria-hidden="true">–</span>
          <input type="date" aria-label=${`Канікули ${i + 1}: останній день`} .value=${live(b)} @change=${(e: Event) => update((x) => setHoliday(x, i, a, (e.target as HTMLInputElement).value))}>
        </span>
        <button type="button" class="ic danger" aria-label=${`Прибрати канікули ${i + 1}`} @click=${() => update((x) => removeHoliday(x, i))}>✕</button>
      </li>`)}</ol>`
      : html`<p class="muted">Канікул ще немає. У ці дні таймер уроків і нагадування відпочивають.</p>`}
    <div class="ed-row">
      <button type="button" ?disabled=${d.holidays.length >= MAX_HOLIDAYS} @click=${() => update((x) => addHoliday(x, today, iso(addDays(new Date(), 6))))}>+ Канікули</button>
    </div>`;
}

/* ---------------- екран ---------------- */

export function scheduleEditor(): TemplateResult {
  const ed = state.editor!;
  const errors = validate(ed.draft);
  const tabs: [EditorTab, string][] = [['lessons', 'Уроки'], ['bells', 'Дзвінки'], ['holidays', 'Канікули']];
  return html`<section class="side editor" aria-labelledby="ed-h" aria-busy=${ed.saving}>
    <div class="ed-head">
      <h2 id="ed-h">${emo('🗓️')} Розклад · ${kidName(ed.kid)}</h2>
      <div class="ed-row">
        <button type="button" @click=${() => void close()}>Скасувати</button>
        <button type="button" class="on" ?disabled=${!dirty() || ed.saving || errors.length > 0} aria-busy=${ed.saving} @click=${() => void save()}>
          ${ed.saving ? 'Зберігаю…' : 'Зберегти'}</button>
      </div>
    </div>
    <nav class="tabs" role="tablist" aria-label="Розділ розкладу">${tabs.map(([t, l]) => html`
      <button type="button" role="tab" aria-selected=${ed.tab === t} class=${classMap({ on: ed.tab === t })} @click=${() => go({ tab: t })}>${l}</button>`)}</nav>
    ${errors.length ? html`<ul class="ed-err" role="alert">${errors.map((e) => html`<li>${e}</li>`)}</ul>` : nothing}
    ${ed.tab === 'lessons' ? lessonsTab(ed.draft, Math.min(ed.day, ed.draft.week.length - 1)) : ed.tab === 'bells' ? bellsTab(ed.draft) : holidaysTab(ed.draft)}
  </section>`;
}

/** Увійшов, але розкладу для вибраної дитини (або сім'ї взагалі) ще немає. */
export const needsSchedule = (): boolean =>
  !!state.me && (state.view === 'main' || state.view === 'join') && !state.timetables.some((t) => t.child_id === state.kid);

/** «Сьогодні» без розкладу: що зробити далі, замість прикладу чужого розкладу. */
export function noSchedule(): TemplateResult {
  const toProfile = () => { location.hash = 'me'; };
  const [text, button] = state.view === 'join' || !state.family
    ? ['Спершу створи сім’ю або приєднайся до неї за кодом від батьків.', html`<button type="button" class="pri-btn" @click=${toProfile}>Перейти в «Профіль»</button>`]
    : canEditSchedule()
      ? ['Додай уроки й дзвінки — і тут з’явиться таймер до дзвінка, рюкзак на день і розклад тижня.', html`<button type="button" class="pri-btn" @click=${openEditor}>${emo('✏️')} Заповнити розклад</button>`]
      : ['У сім’ї ще немає дитини. Дай їй код запрошення з «Профілю» — і заповніть розклад разом.', html`<button type="button" class="pri-btn" @click=${toProfile}>Код запрошення</button>`];
  return html`<section class="side empty no-sched" aria-labelledby="ns-h">
    <div class="empty-ic">${emo('🗓️')}</div>
    <h2 class="h4" id="ns-h">Розклад ще не заповнено</h2>
    <p>${text}</p>
    ${button}
  </section>`;
}

/** Кнопка входу в редактор під розкладом тижня. */
export function editScheduleButton(): TemplateResult | typeof nothing {
  if (!canEditSchedule() || state.editor) return nothing;
  const has = state.timetables.some((t) => t.child_id === state.kid);
  return html`<div class="ed-open"><button type="button" class="on" @click=${openEditor}>${emo('✏️')} ${has ? 'Редагувати розклад' : 'Заповнити розклад'}</button></div>`;
}
