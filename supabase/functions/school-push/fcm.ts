/**
 * Firebase Cloud Messaging (HTTP v1) без сторонніх бібліотек: лише WebCrypto й fetch.
 * Файл без Deno-специфіки — тестується у vitest (tests/unit/fcm.test.ts).
 */

export interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

export interface Push {
  title: string;
  body: string;
  /** Однаковий tag замінює попереднє сповіщення (напр., та сама оцінка). */
  tag: string;
  /** Куди вести при натисканні: «#grades», «#hw». */
  url: string;
  /** Канал Android: family — оцінки й гроші, homework — домашка. */
  channel: 'family' | 'homework';
}

export type SendResult = 'ok' | 'unregistered' | 'error';

const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';

/** Розбирає JSON сервісного акаунта; null — якщо не налаштовано або зламано (тоді Android-сповіщення пропускаються). */
export function parseServiceAccount(raw: string | undefined | null): ServiceAccount | null {
  if (!raw) return null;
  try {
    const o = JSON.parse(raw) as Partial<ServiceAccount>;
    return o.client_email && o.private_key && o.project_id ? { client_email: o.client_email, private_key: o.private_key, project_id: o.project_id } : null;
  } catch {
    return null;
  }
}

const enc = new TextEncoder();

export function base64url(data: Uint8Array | string): string {
  const bytes = typeof data === 'string' ? enc.encode(data) : data;
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function pemToDer(pem: string): Uint8Array<ArrayBuffer> {
  const bin = atob(pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, ''));
  const der = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) der[i] = bin.charCodeAt(i);
  return der;
}

/** JWT для обміну на OAuth-токен Google (RS256, 1 година). */
export async function signJwt(sa: ServiceAccount, nowSec: number): Promise<string> {
  const header = { alg: 'RS256', typ: 'JWT' };
  const claim = { iss: sa.client_email, scope: SCOPE, aud: TOKEN_URL, iat: nowSec, exp: nowSec + 3600 };
  const unsigned = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(claim))}`;
  const key = await crypto.subtle.importKey('pkcs8', pemToDer(sa.private_key), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const sig = new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, enc.encode(unsigned)));
  return `${unsigned}.${base64url(sig)}`;
}

/** Кешований OAuth-токен доступу до FCM (оновлюється за хвилину до закінчення). */
export class FcmAuth {
  private cached: { value: string; exp: number } | null = null;
  constructor(private readonly sa: ServiceAccount, private readonly fetchFn: typeof fetch = fetch) {}

  async token(nowSec = Math.floor(Date.now() / 1000)): Promise<string> {
    if (this.cached && this.cached.exp - 60 > nowSec) return this.cached.value;
    const assertion = await signJwt(this.sa, nowSec);
    const r = await this.fetchFn(TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }).toString(),
    });
    if (!r.ok) throw new Error(`FCM auth ${r.status}`);
    const j = (await r.json()) as { access_token: string; expires_in?: number };
    this.cached = { value: j.access_token, exp: nowSec + (j.expires_in ?? 3600) };
    return j.access_token;
  }
}

/**
 * Тіло запиту FCM v1. Адресат — Firebase Installation ID (поле `fid`): так його видає
 * firebase-messaging 26 (register()/onRegistered); поле `token` застаріле.
 * notification показує система, коли застосунок не відкритий.
 */
export function fcmMessage(fid: string, p: Push) {
  return {
    message: {
      fid,
      notification: { title: p.title, body: p.body },
      data: { url: p.url, tag: p.tag, channel: p.channel },
      android: { priority: 'high', ttl: '86400s', notification: { channel_id: p.channel, tag: p.tag } },
    },
  };
}

/** 404 / UNREGISTERED — токен більше не дійсний (застосунок видалено), його треба прибрати з бази. */
export function classify(status: number, body: string): SendResult {
  if (status >= 200 && status < 300) return 'ok';
  if (status === 404 || /UNREGISTERED/.test(body)) return 'unregistered';
  return 'error';
}

export async function sendFcm(auth: FcmAuth, projectId: string, token: string, p: Push, fetchFn: typeof fetch = fetch): Promise<SendResult> {
  const r = await fetchFn(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${await auth.token()}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(fcmMessage(token, p)),
  });
  return classify(r.status, r.ok ? '' : await r.text());
}
