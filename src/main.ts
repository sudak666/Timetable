import '@fontsource-variable/nunito/wght.css';
import './styles.css';
import { html, render, type TemplateResult } from 'lit-html';
import { iso } from './lib/time';
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

const toggleTheme = () => {
  const dark = root.dataset.theme ? root.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
  root.dataset.theme = dark ? 'light' : 'dark';
  store.set('theme', root.dataset.theme);
};

const bell = html`<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.9 1.9 0 0 0 3.4 0"/></svg>`;
const sunMoon = html`<svg class="sun" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg><svg class="moon" viewBox="0 0 24 24" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/></svg>`;

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

const loading = html`<section class="side card" aria-busy="true"><p class="msg" role="status">Завантаження…</p></section>`;

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
      </div>
    </header>
    <nav class="bnav" aria-label="Розділи">${NAV.map((n) => {
      const b = n.tab === 'grades' ? badges.grades : n.tab === 'hw' ? badges.hw : 0;
      return html`<a href=${'#' + n.tab} class=${state.tab === n.tab ? 'on' : ''} aria-current=${state.tab === n.tab ? 'page' : 'false'}>
        ${n.icon}<span>${n.label}</span>${b ? html`<b class="nb" aria-label=${`${b} нових`}>${b}</b>` : ''}</a>`;
    })}</nav>
    <main id="main-content" tabindex="-1" class="page" data-tab=${state.tab}>${page()}</main>
  </div>`;
};

/* ---- маршрутизація через hash: працює кнопка «назад», можна ділитися посиланням на вкладку ---- */
const readTab = (): Tab => { const h = location.hash.slice(1) as Tab; return TABS.includes(h) ? h : 'today'; };
state.tab = readTab();
addEventListener('hashchange', () => {
  if (location.hash.includes('access_token')) return; // OAuth-редирект Supabase
  set({ tab: readTab() });
  scrollTo({ top: 0 });
  requestAnimationFrame(() => document.getElementById('main-content')?.focus({ preventScroll: true }));
});

const mount = document.getElementById('app')!;
mount.textContent = '';
const draw = () => render(app(), mount);
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
