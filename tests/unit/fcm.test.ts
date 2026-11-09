import { describe, expect, it } from 'vitest';
import { FcmAuth, base64url, classify, fcmMessage, parseServiceAccount, sendFcm, signJwt, type ServiceAccount } from '../../supabase/functions/school-push/fcm.ts';

/** Справжня пара ключів RSA — перевіряємо, що підпис JWT валідний для Google. */
async function testAccount(): Promise<{ sa: ServiceAccount; publicKey: CryptoKey }> {
  const pair = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify'],
  );
  const der = new Uint8Array(await crypto.subtle.exportKey('pkcs8', pair.privateKey));
  const b64 = btoa(String.fromCharCode(...der)).replace(/(.{64})/g, '$1\n');
  const pem = `-----BEGIN PRIVATE KEY-----\n${b64}\n-----END PRIVATE KEY-----\n`;
  return { sa: { client_email: 'push@test.iam.gserviceaccount.com', private_key: pem, project_id: 'dvanadtsiatka-test' }, publicKey: pair.publicKey };
}

const fromB64url = (s: string) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (s.length % 4)) % 4)), (c) => c.charCodeAt(0));

describe('сервісний акаунт', () => {
  it('порожній або зламаний JSON — Android-сповіщення вимкнені, без падіння', () => {
    expect(parseServiceAccount(undefined)).toBeNull();
    expect(parseServiceAccount('{не json')).toBeNull();
    expect(parseServiceAccount('{"client_email":"a"}')).toBeNull();
  });
  it('бере лише потрібні поля', () => {
    expect(parseServiceAccount('{"client_email":"a","private_key":"k","project_id":"p","extra":1}')).toEqual({ client_email: 'a', private_key: 'k', project_id: 'p' });
  });
});

describe('JWT для Google OAuth', () => {
  it('підписаний RS256 і перевіряється відкритим ключем', async () => {
    const { sa, publicKey } = await testAccount();
    const jwt = await signJwt(sa, 1_000_000);
    const [h, c, s] = jwt.split('.') as [string, string, string];
    expect(JSON.parse(new TextDecoder().decode(fromB64url(h)))).toEqual({ alg: 'RS256', typ: 'JWT' });
    const claim = JSON.parse(new TextDecoder().decode(fromB64url(c))) as Record<string, unknown>;
    expect(claim).toMatchObject({ iss: sa.client_email, aud: 'https://oauth2.googleapis.com/token', iat: 1_000_000, exp: 1_003_600 });
    const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', publicKey, fromB64url(s), new TextEncoder().encode(`${h}.${c}`));
    expect(ok).toBe(true);
  });
  it('base64url без «+ / =»', () => {
    expect(base64url(new Uint8Array([251, 255, 254]))).toBe('-__-');
  });
});

describe('токен доступу кешується', () => {
  it('другий виклик не йде в мережу, після закінчення — йде', async () => {
    const { sa } = await testAccount();
    let calls = 0;
    const fake = (async () => { calls++; return new Response(JSON.stringify({ access_token: 't' + calls, expires_in: 3600 })); }) as typeof fetch;
    const auth = new FcmAuth(sa, fake);
    expect(await auth.token(100)).toBe('t1');
    expect(await auth.token(200)).toBe('t1');
    expect(await auth.token(100 + 3600)).toBe('t2');
    expect(calls).toBe(2);
  });
});

describe('повідомлення й відповіді FCM', () => {
  const p = { title: '✅ Математика: 12 підтверджено', body: 'Нараховано +300 ₴', tag: 'g1', url: '#grades', channel: 'family' as const };
  it('адресат — Firebase Installation ID у полі fid (token застарів)', () => {
    const m = fcmMessage('fid-22-chars-abcdefghij', p).message as Record<string, unknown>;
    expect(m.fid).toBe('fid-22-chars-abcdefghij');
    expect('token' in m).toBe(false);
  });
  it('канал і tag потрапляють в android.notification', () => {
    const m = fcmMessage('tok', p).message;
    expect(m.android.notification).toEqual({ channel_id: 'family', tag: 'g1' });
    expect(m.data).toEqual({ url: '#grades', tag: 'g1', channel: 'family' });
  });
  it('класифікація помилок', () => {
    expect(classify(200, '')).toBe('ok');
    expect(classify(404, '')).toBe('unregistered');
    expect(classify(400, '{"error":{"details":[{"errorCode":"UNREGISTERED"}]}}')).toBe('unregistered');
    expect(classify(500, 'boom')).toBe('error');
  });
  it('sendFcm шле на правильний проєкт з токеном доступу', async () => {
    const { sa } = await testAccount();
    const seen: { url: string; auth: string | null }[] = [];
    const fake = (async (url: string | URL | Request, init?: RequestInit) => {
      const u = String(url);
      if (u.includes('oauth2')) return new Response(JSON.stringify({ access_token: 'AT' }));
      seen.push({ url: u, auth: new Headers(init?.headers).get('Authorization') });
      return new Response('{}', { status: 200 });
    }) as typeof fetch;
    expect(await sendFcm(new FcmAuth(sa, fake), sa.project_id, 'tok', p, fake)).toBe('ok');
    expect(seen).toEqual([{ url: 'https://fcm.googleapis.com/v1/projects/dvanadtsiatka-test/messages:send', auth: 'Bearer AT' }]);
  });
});
