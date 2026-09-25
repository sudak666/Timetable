import '@fontsource-variable/nunito/wght.css';
import './styles.css';
import { html, render } from 'lit-html';
import { iso } from './lib/time';
import { set, state, store, subscribe, type GameStyle } from './state';
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

const app = () => {
  const d = state.now;
  return html`<a class="skip" href="#main">Перейти до розкладу</a>
  <div class="wrap">
    <header>
      <div><h1>Розклад уроків</h1><p class="clock"><time datetime=${d.toISOString()}>${d.toLocaleDateString('uk-UA', { weekday: 'long', day: 'numeric', month: 'long' })} · ${d.toLocaleTimeString('uk-UA')}</time></p></div>
      <div class="tools">
        <button type="button" class=${state.lessonAlerts ? 'ic on' : 'ic'} aria-pressed=${state.lessonAlerts} aria-label="Нагадування за 2 хвилини до уроку" @click=${toggleLessonAlerts}>${bell}</button>
        <button type="button" class="ic" aria-label="Перемкнути світлу / темну тему" @click=${toggleTheme}>${sunMoon}</button>
      </div>
    </header>
    ${G?.profileBar() ?? ''}
    ${bikeLane(G ? G.laneLabel() : emo('⚡ Електробайк мрії'))}
    <div class="top">${nowCard()}${sidePanel()}</div>
    ${funCards(quizView())}
    <main id="main" tabindex="-1">${weekGrid()}</main>
    ${G ? G.gradesSection() : html`<section class="side gr" aria-busy="true"><p class="msg" role="status">Завантаження…</p></section>`}
    ${weekStats()}
  </div>`;
};

const mount = document.getElementById('app')!;
mount.textContent = '';
const draw = () => render(app(), mount);
subscribe(draw);
draw();

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

void Promise.all([import('./views/grades'), import('./api'), import('./pwa')]).then(([g, api, pwa]) => {
  G = g;
  api.watchAuth();
  void pwa.initPwa();
  draw();
});
