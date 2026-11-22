/**
 * Чи витікав пароль в інтернет — база Have I Been Pwned (безкоштовна заміна платної перевірки Supabase).
 * k-анонімність: з браузера йде лише перший 5-символьний шматок SHA-1, збіг шукаємо самі.
 * Ні пароль, ні його повний хеш нікуди не потрапляють. Так само робить Android (LeakedPasswords.kt).
 */
const RANGE = 'https://api.pwnedpasswords.com/range/';

export async function sha1(s: string): Promise<string> {
  const d = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, '0')).join('').toUpperCase();
}

/** Відповідь — рядки «СУФІКС:КІЛЬКІСТЬ»; заповнювачі (Add-Padding) мають кількість 0. */
export function leakCount(body: string, suffix: string): number {
  const want = suffix.toUpperCase();
  for (const line of body.split('\n')) {
    const [s, n] = line.trim().split(':');
    if (s?.toUpperCase() === want) return Number(n) || 0;
  }
  return 0;
}

/** true — пароль у базі злитих; false — ні; null — перевірити не вдалося (немає мережі): не заважаємо. */
export async function isLeaked(password: string, fetcher: typeof fetch = fetch): Promise<boolean | null> {
  try {
    const h = await sha1(password);
    const r = await fetcher(RANGE + h.slice(0, 5), { headers: { 'Add-Padding': 'true' }, signal: AbortSignal.timeout(4000) });
    if (!r.ok) return null;
    return leakCount(await r.text(), h.slice(5)) > 0;
  } catch {
    return null;
  }
}

/** Мінімум для нового пароля (вхід зі старим від 6 символів працює й далі). */
export const MIN_NEW_PASSWORD = 8;

/** Що не так із новим паролем (null — можна зберігати). */
export async function newPasswordProblem(p: string, fetcher: typeof fetch = fetch): Promise<string | null> {
  if (p.length < MIN_NEW_PASSWORD) return `Пароль має бути від ${MIN_NEW_PASSWORD} символів`;
  return (await isLeaked(p, fetcher)) ? 'Цей пароль уже витікав в інтернеті — придумай інший' : null;
}
