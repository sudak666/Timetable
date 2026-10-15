/** Стан у URL: `#grades?kid=…`, `#today?day=2`. Фільтри не створюють записів в історії. */
export interface Route<T extends string> { tab: T; day: number | null; kid: string | null }

export function parseRoute<T extends string>(hash: string, tabs: readonly T[], fallback: T): Route<T> {
  const [path = '', query = ''] = hash.replace(/^#/, '').split('?');
  const p = new URLSearchParams(query);
  const d = Number(p.get('day'));
  const kid = p.get('kid');
  return {
    tab: (tabs as readonly string[]).includes(path) ? (path as T) : fallback,
    day: p.has('day') && Number.isInteger(d) && d >= 0 && d <= 4 ? d : null,
    kid: kid && /^[\w-]{1,64}$/.test(kid) ? kid : null,
  };
}

export function formatRoute<T extends string>(r: Route<T>): string {
  const p = new URLSearchParams();
  if (r.tab === 'today' && r.day !== null) p.set('day', String(r.day));
  if (r.tab !== 'today' && r.kid) p.set('kid', r.kid);
  const q = p.toString();
  return '#' + r.tab + (q ? '?' + q : '');
}
