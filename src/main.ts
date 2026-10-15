import '@fontsource-variable/nunito/wght.css';
import './styles.css';
import { html, render, type TemplateResult } from 'lit-html';
import { iso } from './lib/time';
import { PASS } from './features/rewards';
import { formatRoute, parseRoute } from './lib/route';
import { parseQuickGrade } from './lib/command';
import { addDays } from './lib/time';
import { SUBJECT_LIST, WEEK } from './data/schedule';
import { openPalette, type Cmd } from './ui/palette';
import { set, state, store, subscribe, TABS, type GameStyle, type Tab } from './state';
import { bikeLane } from './ui/bike';
import { boom } from './ui/confetti';
import { applyAccent, applyStyle } from './ui/theme';
import { emo } from './lib/emoji';
import { quizView } from './views/quiz';
import { funCards, lessonAlertTick, nowCard, sidePanel, toggleLessonAlerts, weekGrid, weekStats } from './views/schedule';
import { clockOf, nextLessonDate, statusAt } from './lib/time';

/* ---- відновлення налаштувань до першого рендера (без миготіння) ---- */
const root = document.documentElement;
const theme = store.get('theme');
if (theme === 'dark' || theme === 'light') root.dataset.theme = theme;
applyAccent(store.get('accent'));
const style = (store.get('style') ?? '') as GameStyle;
if (style === 'arena' || style === 'blocks') { applyStyle(style); state.style = style; }
try {
  const o = JSON.parse(store.get('stars') ?? '{}') as { d?: string; n?: number };
  if (o.d === iso(new Date())) state.stars = Math.min(10, Number(o.n) || 0);
} catch { /* ignore */ }
state.lessonAlerts = store.get('notif') === '1' && typeof Notification !== 'undefined' && Notification.permission === 'granted';
state.form.date = iso(new Date());
state.form.hwDate = nextLessonDate(state.form.hwSubject, new Date());

/** theme-color у браузері має збігатися з обраною вручну темою, а не лише з системною. */
const syncThemeColor = () => {
  const t = root.dataset.theme;
  for (const m of document.querySelectorAll<HTMLMetaElement>('meta[name=theme-color]')) {
    if (!m.dataset.media) m.dataset.media = m.media;
    m.media = t ? (m.dataset.media.includes(t) ? 'all' : 'not all') : m.dataset.media;
  }
};
syncThemeColor();
const toggleTheme = () => {
  const dark = root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
  root.dataset.theme = dark ? 'light' : 'dark';
  store.set('theme', root.dataset.theme);
  syncThemeColor();
};

const bell = html`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.9 1.9 0 0 0 3.4 0"/></svg>`;
const sunMoon = html`<svg class="sun" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg><svg class="moon" viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>`;

const searchIc = html`<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>`;

/* ---- Command Palette + гарячі клавіші ---- */
const go = (tab: Tab) => { location.hash = tab; };
const baseCmds = (): Cmd[] => {
  const kids = state.role === 'parent' ? state.members.filter((m) => m.role === 'child') : [];
  return [
    ...NAV.map((n, i): Cmd => ({ id: 'tab-' + n.tab, label: n.label, hint: 'Перейти', keys: String(i + 1), run: () => go(n.tab) })),
    ...WEEK.map((d, i): Cmd => ({ id: 'day-' + i, label: '📅 ' + d.name, hint: 'Розклад на день', run: () => { set({ day: i }); go('today'); } })),
    { id: 'week', label: '🗓️ Весь тиждень', hint: 'Розклад', run: () => { set({ day: null }); go('today'); } },
    ...kids.map((k): Cmd => ({ id: 'kid-' + k.user_id, label: `${k.avatar || '🧒'} ${k.name || 'Дитина'}`, hint: 'Обрати дитину', run: () => set({ kid: k.user_id }) })),
    { id: 'theme', label: '🌗 Світла / темна тема', keys: 'T', run: toggleTheme },
    { id: 'alerts', label: state.lessonAlerts ? '🔕 Вимкнути нагадування про уроки' : '🔔 Нагадування за 2 хв до уроку', run: toggleLessonAlerts },
  ];
};
const DAY_WORD = ['сьогодні', 'вчора', 'позавчора'];
const quickCmds = (q: string): Cmd[] => {
  if (!G || state.view !== 'main' || !state.kid) return [];
  const r = parseQuickGrade(q, SUBJECT_LIST, PASS);
  if (!r) return [];
  const date = iso(addDays(new Date(), r.offset));
  const who = state.members.find((m) => m.user_id === state.kid)?.name;
  return [{
    id: 'quick', label: `➕ ${r.grade === PASS ? 'Зараховано' : r.grade} · ${r.subject}`, hint: `${DAY_WORD[-r.offset]}${who && state.role === 'parent' ? ' · ' + who : ''}`,
    run: () => { go('grades'); return G!.quickGrade(r.grade, r.subject, date); },
  }];
};
const palette = () => openPalette(baseCmds, quickCmds);

addEventListener('keydown', (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); palette(); return; }
  const t = e.target as HTMLElement;
  if (e.metaKey || e.ctrlKey || e.altKey || e.defaultPrevented || t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || document.querySelector('dialog[open]')) return;
  const n = NAV[Number(e.key) - 1];
  if (n) go(n.tab);
  else if (e.key === '/') { e.preventDefault(); palette(); }
  else if (e.key === 't' || e.key === 'T' || e.key === 'е' || e.key === 'Е') toggleTheme();
});

/** Модуль оцінок (Supabase ~70 КБ gzip) вантажиться окремим чанком після першого рендера розкладу. */
let G: typeof import('./views/grades') | null = null;

const NAV: { tab: Tab; label: string; icon: TemplateResult }[] = [
  { tab: 'today', label: 'Сьогодні', icon: html`<svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4.5" width="18" height="16" rx="3"/><path d="M3 9.5h18M8 2.5v4M16 2.5v4"/><circle cx="12" cy="15" r="2.2"/></svg>` },
  { tab: 'grades', label: 'Оцінки', icon: html`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0z"/><path d="M17 5h3a3 3 0 0 1-3 4M7 5H4a3 3 0 0 0 3 4"/></svg>` },
  { tab: 'hw', label: 'Домашка', icon: html`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v15H6.5A2.5 2.5 0 0 0 4 20.5z"/><path d="M4 20.5A2.5 2.5 0 0 0 6.5 23H20v-5M9 8h7M9 12h5"/></svg>` },
  { tab: 'me', label: 'Профіль', icon: html`<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>` },
];

/** Перший кадр — лише верх сторінки; решта домальовується в простої (коротший long task → нижчий TBT). */
let below = false;
const showBelow = () => { below = true; draw(); };

const loading = html`<section class="side card" aria-busy="true" aria-label="Завантаження">
  <div class="sk sk-h"></div><div class="sk-row"><div class="sk sk-b"></div><div class="sk sk-b"></div></div><div class="sk sk-l"></div><div class="sk sk-l short"></div></section>`;

function page(): TemplateResult {
  switch (state.tab) {
    case 'grades': return G ? G.tabGrades() : loading;
    case 'hw': return G ? G.tabHomework() : loading;
    case 'me': return G ? G.tabProfile() : loading;
    default: return html`${G?.profileBar() ?? ''}
      ${bikeLane(G ? G.laneLabel() : emo('⚡ Електробайк мрії'))}
      <div class="top">${nowCard()}${sidePanel()}</div>
      ${below ? html`${funCards(quizView())}${weekGrid()}${weekStats()}` : html`<div class="below-ph" aria-hidden="true"></div>`}`;
  }
}

const app = () => {
  const d = state.now, badges = G?.navBadges() ?? { grades: 0, hw: 0 };
  return html`<a class="skip" href="#main-content">Перейти до вмісту</a>
  <div class="wrap">
    <header>
      <div><h1>Розклад уроків</h1><p class="clock"><time datetime=${d.toISOString()}>${d.toLocaleDateString('uk-UA', { weekday: 'long', day: 'numeric', month: 'long' })} · ${d.toLocaleTimeString('uk-UA')}</time></p></div>
      <div class="tools">
        <button type="button" class=${state.lessonAlerts ? 'ic on' : 'ic'} aria-pressed=${state.lessonAlerts} aria-label="Нагадування за 2 хвилини до уроку" @click=${toggleLessonAlerts}>${bell}</button>
        <button type="button" class="ic" aria-label="Перемкнути світлу / темну тему" @click=${toggleTheme}>${sunMoon}</button>
        <button type="button" class="ic" aria-label="Команди" aria-keyshortcuts="Control+K Meta+K" title="Команди (Ctrl+K)" @click=${palette}>${searchIc}</button>
      </div>
    </header>
    ${state.online ? state.queued ? html`<p class="offline sync" role="status">${emo('🔄')} Синхронізація: ${state.queued} змін…</p>` : ''
      : html`<p class="offline" role="status">${emo('📡')} Немає інтернету — показано збережене. Оцінки й домашка ${state.queued ? html`(<b>${state.queued}</b> у черзі) ` : ''}відправляться, щойно зʼявиться звʼязок.</p>`}
    <nav class="bnav" aria-label="Розділи">${NAV.map((n) => {
      const b = n.tab === 'grades' ? badges.grades : n.tab === 'hw' ? badges.hw : 0;
      return html`<a href=${'#' + n.tab} class=${state.tab === n.tab ? 'on' : ''} aria-current=${state.tab === n.tab ? 'page' : 'false'}>
        ${n.icon}<span>${n.label}</span>${b ? html`<b class="nb" aria-label=${`${b} нових`}>${b}</b>` : ''}</a>`;
    })}</nav>
    <main id="main-content" tabindex="-1" class="page" data-tab=${state.tab}>${page()}</main>
  </div>`;
};

/* ---- маршрутизація через hash: працює кнопка «назад», можна ділитися посиланням на вкладку ---- */
const isAuthHash = () => /access_token|error_description/.test(location.hash); // OAuth-редирект Supabase
const readRoute = () => parseRoute<Tab>(location.hash, TABS, 'today');
if (!isAuthHash()) { const r = readRoute(); state.tab = r.tab; state.day = r.day; state.kid = r.kid; }
/** Фільтри (день, дитина) — у URL без нових записів в історії. */
const syncUrl = () => {
  // Навігація, ще не оброблена hashchange, має пріоритет над станом.
  if (isAuthHash() || (location.hash && readRoute().tab !== state.tab)) return;
  const h = formatRoute<Tab>({ tab: state.tab, day: state.day, kid: state.kid });
  if (h !== location.hash && !(h === '#today' && !location.hash)) history.replaceState(history.state, '', h);
};
addEventListener('hashchange', () => {
  if (isAuthHash()) return;
  const r = readRoute(), prev = state.tab;
  set({ tab: r.tab, day: r.day, ...(r.kid ? { kid: r.kid } : {}) });
  if (r.tab === prev) return;
  scrollTo({ top: 0 });
  requestAnimationFrame(() => document.getElementById('main-content')?.focus({ preventScroll: true }));
});

const mount = document.getElementById('app')!;
mount.textContent = '';
const draw = () => { render(app(), mount); syncUrl(); };
subscribe(draw);
draw();
if ('requestIdleCallback' in window) requestIdleCallback(showBelow, { timeout: 600 });
else setTimeout(showBelow, 50);

/* ---- годинник: 1 оновлення стану за секунду ---- */
let wasDone = statusAt(clockOf(new Date())).kind === 'done';
setInterval(() => {
  set({ now: new Date() });
  lessonAlertTick();
  const done = statusAt(clockOf(state.now)).kind === 'done';
  if (done && !wasDone) boom();
  wasDone = done;
}, 1000);
addEventListener('online', () => set({ online: true }));
addEventListener('offline', () => set({ online: false }));
document.addEventListener('visibilitychange', () => { if (!document.hidden) set({ now: new Date() }); });

/* Модуль даних вантажимо в простої браузера, щоб не блокувати першу взаємодію (TBT/INP).
   Одразу — якщо відкрита вкладка, якій потрібні дані, або це повернення з OAuth. */
const loadData = () => Promise.all([import('./views/grades'), import('./api'), import('./pwa')]).then(([g, api, pwa]) => {
  G = g;
  api.watchAuth();
  void pwa.initPwa();
  draw();
});
if (state.tab !== 'today' || /access_token|code=/.test(location.href)) void loadData();
else if ('requestIdleCallback' in window) requestIdleCallback(() => void loadData(), { timeout: 1500 });
else setTimeout(() => void loadData(), 300);
addEventListener('hashchange', () => { if (!G) void loadData(); }, { once: true });
