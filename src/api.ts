import { AuthClient } from '@supabase/auth-js';
import { PostgrestClient } from '@supabase/postgrest-js';
import type { RealtimeChannel, RealtimeClient } from '@supabase/realtime-js';
import { enqueue, isOp, overlay, type Op } from './features/outbox';
import { parseSettings, type Challenge, type Grade, type Homework, type Ledger, type Member, type Role } from './features/rewards';
import { set, state, store } from './state';
import { applyAccent } from './ui/theme';
import { toast } from './ui/toast';

const URL = 'https://vkwkyhjjjmcpmiakxohw.supabase.co';
/** Публічний (publishable) ключ — безпечний на клієнті; доступ до даних обмежує RLS. */
const KEY = 'sb_publishable_KV2ZYS0ELpHPO9cX10Z9Tw_veUObkM9';

const REF = new globalThis.URL(URL).hostname.split('.')[0]!;

/**
 * Модульний клієнт замість повного @supabase/supabase-js (58 → 30 КБ gzip):
 * лише auth + PostgREST; realtime довантажується окремим чанком після входу.
 * storageKey збігається з supabase-js — наявні сесії користувачів зберігаються.
 */
const auth = new AuthClient({
  url: `${URL}/auth/v1`,
  headers: { apikey: KEY, Authorization: `Bearer ${KEY}` },
  storageKey: `sb-${REF}-auth-token`,
  persistSession: true,
  autoRefreshToken: true,
  detectSessionInUrl: true,
  flowType: 'implicit',
});

const authedFetch: typeof fetch = async (input, init) => {
  const { data } = await auth.getSession();
  const headers = new Headers(init?.headers);
  headers.set('apikey', KEY);
  headers.set('Authorization', `Bearer ${data.session?.access_token ?? KEY}`);
  return fetch(input, { ...init, headers });
};
const rest = new PostgrestClient(`${URL}/rest/v1`, { fetch: authedFetch });

export const sb = {
  auth,
  from: (table: string) => rest.from(table),
  rpc: (fn: string, args: Record<string, unknown>) => rest.rpc(fn, args),
};

let realtime: RealtimeClient | null = null;
async function getRealtime(): Promise<RealtimeClient> {
  if (!realtime) {
    const { RealtimeClient: RC } = await import('@supabase/realtime-js');
    realtime = new RC(`${URL.replace('https', 'wss')}/realtime/v1`, { params: { apikey: KEY } });
  }
  const { data } = await auth.getSession();
  if (data.session) realtime.setAuth(data.session.access_token);
  return realtime;
}

/** Людські повідомлення замість технічних помилок мережі/БД. */
export function errText(e: unknown): string {
  const m = e && typeof e === 'object' && 'message' in e ? String((e as { message: unknown }).message) : String(e);
  if (!navigator.onLine || /Failed to fetch|NetworkError|Load failed/i.test(m)) return 'Немає інтернету — спробуй ще раз, коли зʼявиться звʼязок';
  if (/JWT|expired|invalid claim/i.test(m)) return 'Сесія застаріла — увійди ще раз';
  if (/row-level security|permission denied|violates/i.test(m)) return 'Недостатньо прав для цієї дії';
  if (/duplicate key/i.test(m)) return 'Такий запис уже існує';
  return m;
}

let channel: RealtimeChannel | null = null;
let reloadTimer: ReturnType<typeof setTimeout> | undefined;

export async function loadFamily(): Promise<void> {
  const fid = state.family?.id;
  if (!fid) return;
  const [f, m, g, l, c, h] = await Promise.all([
    sb.from('school_families').select('id,name,invite_code,rates').eq('id', fid).single(),
    sb.from('school_members').select('user_id,role,name,goal_title,goal_amount,avatar,theme').eq('family_id', fid),
    sb.from('school_grades').select('id,child_id,subject,grade,date,status,amount,created_at').eq('family_id', fid).order('date', { ascending: false }).limit(2000),
    sb.from('school_ledger').select('id,child_id,kind,amount,note,date,created_at').eq('family_id', fid).order('date', { ascending: false }).limit(2000),
    sb.from('school_challenges').select('id,child_id,title,subject,min_grade,need,reward,start_date,end_date').eq('family_id', fid).limit(200),
    sb.from('school_homework').select('id,child_id,subject,due,text,done').eq('family_id', fid).order('due').limit(1000),
  ]);
  const firstErr = [f, m, g, l, c, h].find((r) => r.error)?.error;
  if (firstErr) throw firstErr;
  const snap: Snapshot = {
    family: f.data ? { id: f.data.id, name: f.data.name, invite_code: f.data.invite_code } : state.family!,
    role: state.role!, rates: f.data?.rates,
    members: (m.data ?? []) as Member[], grades: (g.data ?? []) as Grade[], ledger: (l.data ?? []) as Ledger[],
    challenges: (c.data ?? []) as Challenge[], homework: (h.data ?? []) as Homework[],
  };
  if (state.me) store.set(SNAP + state.me.id, JSON.stringify(snap));
  applySnapshot(snap, false);
  // Колір акценту — персональний; кешуємо, щоб наступне відкриття було без миготіння.
  const theme = state.members.find((x) => x.user_id === state.me?.id)?.theme || null;
  store.set('accent', theme);
  applyAccent(theme);
}

/* ---------------- офлайн: знімок даних + черга змін ---------------- */
const SNAP = 'snap:', OUTBOX = 'outbox:';
interface Snapshot {
  family: { id: string; name: string; invite_code: string }; role: Role; rates: unknown;
  members: Member[]; grades: Grade[]; ledger: Ledger[]; challenges: Challenge[]; homework: Homework[];
}
let server: Pick<Snapshot, 'grades' | 'homework'> = { grades: [], homework: [] };

const readQueue = (): Op[] => {
  if (!state.me) return [];
  try { const v: unknown = JSON.parse(store.get(OUTBOX + state.me.id) ?? '[]'); return Array.isArray(v) ? v.filter(isOp) : []; } catch { return []; }
};
const writeQueue = (q: Op[]) => { if (state.me) store.set(OUTBOX + state.me.id, q.length ? JSON.stringify(q) : null); };

function applySnapshot(x: Snapshot, stale: boolean): void {
  server = { grades: x.grades, homework: x.homework };
  const q = readQueue(), o = overlay(q, x.grades, x.homework);
  const { rates, streak } = parseSettings(x.rates);
  set((s) => {
    s.family = x.family;
    s.role = x.role;
    s.rates = rates;
    s.streak = streak;
    s.members = x.members;
    s.grades = o.grades;
    s.ledger = x.ledger;
    s.challenges = x.challenges;
    s.homework = o.homework;
    s.queued = q.length;
    s.stale = stale;
    const kids = s.members.filter((k) => k.role === 'child');
    if (s.role === 'child') s.kid = s.me!.id;
    else if (!kids.some((k) => k.user_id === s.kid)) s.kid = kids[0]?.user_id ?? null;
  });
}

/** Офлайн-старт: показуємо останні збережені дані цього користувача. */
function restoreSnapshot(u: { id: string; email?: string | undefined; user_metadata?: { full_name?: string } }): boolean {
  const userId = u.id;
  try {
    if (!state.me) set({ me: { id: u.id, email: u.email ?? '', fullName: u.user_metadata?.full_name ?? '' } });
    const x = JSON.parse(store.get(SNAP + userId) ?? 'null') as Snapshot | null;
    if (!x?.family?.id || !Array.isArray(x.grades)) return false;
    applySnapshot(x, true);
    set({ view: 'main' });
    return true;
  } catch { return false; }
}

const isNetErr = (e: unknown) => !navigator.onLine || /Failed to fetch|NetworkError|Load failed|fetch failed/i.test(errText(e) + String((e as { message?: unknown })?.message ?? ''));

/** Зміна, що переживає відсутність мережі: одразу видно в UI, на сервер іде при першій нагоді. */
export async function mutate(op: Op): Promise<void> {
  const q = enqueue(readQueue(), op);
  writeQueue(q);
  const o = overlay(q, server.grades, server.homework);
  set({ grades: o.grades, homework: o.homework, queued: q.length });
  await flush();
}

let flushing: Promise<void> | null = null;
export function flush(): Promise<void> {
  flushing ??= (async () => {
    let q = readQueue(), changed = false;
    while (q.length && navigator.onLine) {
      const op = q[0]!;
      const { error } = op.kind === 'insert'
        ? await sb.from(op.t).upsert({ ...op.row }, { onConflict: 'id', ignoreDuplicates: true })
        : await sb.from(op.t).update(op.patch).eq('id', op.id);
      if (error && isNetErr(error)) break;
      if (error) toast('Зміну не збережено: ' + errText(error), 'err');
      q = q.slice(1);
      writeQueue(q);
      changed = true;
    }
    set({ queued: q.length });
    if (changed) await loadFamily().catch(() => undefined);
  })().finally(() => { flushing = null; });
  return flushing;
}
addEventListener('online', () => void flush());

const scheduleReload = () => {
  clearTimeout(reloadTimer);
  reloadTimer = setTimeout(() => void loadFamily().catch(() => undefined), 250);
};

export async function enter(): Promise<void> {
  const { data: { user }, error: uErr } = await sb.auth.getUser();
  if (uErr && isNetErr(uErr)) throw uErr;
  if (!user) return set({ view: 'auth', me: null, family: null, role: null });
  const meta = user.user_metadata as { full_name?: string } | undefined;
  set({ me: { id: user.id, email: user.email ?? '', fullName: meta?.full_name ?? '' } });
  const { data: mem, error } = await sb.from('school_members').select('family_id,role').eq('user_id', user.id).limit(1);
  if (error) throw error;
  const row = mem?.[0];
  if (!row) return set({ view: 'join' });
  set({ family: { id: row.family_id as string, name: '', invite_code: '' }, role: row.role as Role });
  await loadFamily();
  set({ view: 'main' });
  if (state.role === 'parent') {
    const { data } = await sb.rpc('school_parent_code', { fid: row.family_id });
    set({ parentCode: (data as string | null) ?? '' });
  }
  const rt = await getRealtime();
  if (channel) await rt.removeChannel(channel);
  const fid = row.family_id as string;
  channel = rt.channel('fam-' + fid);
  for (const t of ['school_grades', 'school_ledger', 'school_members', 'school_challenges', 'school_homework'])
    channel.on('postgres_changes', { event: '*', schema: 'public', table: t, filter: `family_id=eq.${fid}` }, scheduleReload);
  channel.on('postgres_changes', { event: '*', schema: 'public', table: 'school_families', filter: `id=eq.${fid}` }, scheduleReload);
  channel.subscribe();
}

export async function signOut(): Promise<void> {
  if (channel && realtime) await realtime.removeChannel(channel);
  channel = null;
  await sb.auth.signOut();
  if (state.me) { store.set(SNAP + state.me.id, null); store.set(OUTBOX + state.me.id, null); }
  set({ view: 'auth', me: null, family: null, role: null, queued: 0, stale: false, members: [], grades: [], ledger: [], challenges: [], homework: [], kid: null });
}

export function watchAuth(): void {
  let cur: string | null | undefined;
  sb.auth.onAuthStateChange((_ev, session) => {
    if (session && realtime) realtime.setAuth(session.access_token);
    const id = session?.user.id ?? null;
    if (id === cur) return;
    cur = id;
    setTimeout(() => void enter().then(flush).catch((e) => {
      if (session && isNetErr(e) && restoreSnapshot(session.user)) return;
      set({ view: 'offline', authMsg: { text: 'Помилка: ' + errText(e) } });
    }), 0);
  });
}

