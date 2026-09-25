import type { Challenge, Grade, Homework, Ledger, Member, Rates, Role, Streak } from './features/rewards';
import { DEFAULT_RATES, DEFAULT_STREAK } from './features/rewards';

export type AuthView = 'loading' | 'auth' | 'join' | 'main' | 'offline';
export type GameStyle = '' | 'arena' | 'blocks';
export type Tab = 'today' | 'grades' | 'hw' | 'me';
export const TABS: Tab[] = ['today', 'grades', 'hw', 'me'];

export interface Family { id: string; name: string; invite_code: string }

export interface State {
  tab: Tab;
  now: Date;
  /** Вкладка розкладу: null = весь тиждень, 0..4 = день */
  day: number | null;
  highlight: string | null;
  lessonAlerts: boolean;
  stars: number;
  factIdx: number;

  view: AuthView;
  authMsg: { text: string; ok?: boolean } | null;
  me: { id: string; email: string; fullName: string } | null;
  family: Family | null;
  parentCode: string;
  role: Role | null;
  members: Member[];
  kid: string | null;
  grades: Grade[];
  ledger: Ledger[];
  challenges: Challenge[];
  homework: Homework[];
  rates: Rates;
  streak: Streak;

  form: { subject: string; date: string; hwSubject: string; hwDate: string; chSubject: string };
  pushOn: boolean;
  pushMsg: { text: string; ok?: boolean } | null;
  canInstall: boolean;
  style: GameStyle;
}

type Listener = () => void;
const listeners = new Set<Listener>();
let scheduled = false;

export const state: State = {
  tab: 'today',
  now: new Date(),
  day: null,
  highlight: null,
  lessonAlerts: false,
  stars: 0,
  factIdx: Math.floor(Date.now() / 864e5),
  view: 'loading',
  authMsg: null,
  me: null,
  family: null,
  parentCode: '',
  role: null,
  members: [],
  kid: null,
  grades: [],
  ledger: [],
  challenges: [],
  homework: [],
  rates: { ...DEFAULT_RATES },
  streak: { ...DEFAULT_STREAK },
  form: { subject: 'Математика', date: '', hwSubject: 'Математика', hwDate: '', chSubject: '' },
  pushOn: false,
  pushMsg: null,
  canInstall: false,
  style: '',
};

/** Мутує стан і планує один рендер на кадр (батчинг). */
export function set(patch: Partial<State> | ((s: State) => void)): void {
  if (typeof patch === 'function') patch(state);
  else Object.assign(state, patch);
  if (scheduled) return;
  scheduled = true;
  requestAnimationFrame(() => {
    scheduled = false;
    listeners.forEach((l) => l());
  });
}
export const subscribe = (l: Listener): void => void listeners.add(l);

/** Безпечний доступ до localStorage (приватний режим, заблоковані cookies). */
export const store = {
  get: (k: string): string | null => {
    try { return localStorage.getItem(k); } catch { return null; }
  },
  set: (k: string, v: string | null): void => {
    try { if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v); } catch { /* ignore */ }
  },
};
