import { createClient, type RealtimeChannel } from '@supabase/supabase-js';
import { parseSettings, type Challenge, type Grade, type Homework, type Ledger, type Member, type Role } from './features/rewards';
import { set, state, store } from './state';
import { applyAccent } from './ui/theme';

const URL = 'https://vkwkyhjjjmcpmiakxohw.supabase.co';
/** Публічний (publishable) ключ — безпечний на клієнті; доступ до даних обмежує RLS. */
const KEY = 'sb_publishable_KV2ZYS0ELpHPO9cX10Z9Tw_veUObkM9';

export const sb = createClient(URL, KEY, { auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true } });

export const errText = (e: unknown): string =>
  e && typeof e === 'object' && 'message' in e ? String((e as { message: unknown }).message) : String(e);

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
  const { rates, streak } = parseSettings(f.data?.rates);
  set((s) => {
    if (f.data) s.family = { id: f.data.id, name: f.data.name, invite_code: f.data.invite_code };
    s.rates = rates;
    s.streak = streak;
    s.members = (m.data ?? []) as Member[];
    s.grades = (g.data ?? []) as Grade[];
    s.ledger = (l.data ?? []) as Ledger[];
    s.challenges = (c.data ?? []) as Challenge[];
    s.homework = (h.data ?? []) as Homework[];
    const kids = s.members.filter((x) => x.role === 'child');
    if (s.role === 'child') s.kid = s.me!.id;
    else if (!kids.some((k) => k.user_id === s.kid)) s.kid = kids[0]?.user_id ?? null;
  });
  // Колір акценту — персональний; кешуємо, щоб наступне відкриття було без миготіння.
  const theme = state.members.find((x) => x.user_id === state.me?.id)?.theme || null;
  store.set('accent', theme);
  applyAccent(theme);
}

const scheduleReload = () => {
  clearTimeout(reloadTimer);
  reloadTimer = setTimeout(() => void loadFamily().catch(() => undefined), 250);
};

export async function enter(): Promise<void> {
  const { data: { user } } = await sb.auth.getUser();
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
  if (channel) await sb.removeChannel(channel);
  const fid = row.family_id as string;
  channel = sb.channel('fam-' + fid);
  for (const t of ['school_grades', 'school_ledger', 'school_members', 'school_challenges', 'school_homework'])
    channel.on('postgres_changes', { event: '*', schema: 'public', table: t, filter: `family_id=eq.${fid}` }, scheduleReload);
  channel.on('postgres_changes', { event: '*', schema: 'public', table: 'school_families', filter: `id=eq.${fid}` }, scheduleReload);
  channel.subscribe();
}

export async function signOut(): Promise<void> {
  if (channel) await sb.removeChannel(channel);
  channel = null;
  await sb.auth.signOut();
  set({ view: 'auth', me: null, family: null, role: null, members: [], grades: [], ledger: [], challenges: [], homework: [], kid: null });
}

export function watchAuth(): void {
  let cur: string | null | undefined;
  sb.auth.onAuthStateChange((_ev, session) => {
    const id = session?.user.id ?? null;
    if (id === cur) return;
    cur = id;
    setTimeout(() => void enter().catch((e) => set({ view: 'offline', authMsg: { text: 'Помилка: ' + errText(e) } })), 0);
  });
}

