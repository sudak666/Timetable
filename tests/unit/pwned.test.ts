import { describe, expect, it } from 'vitest';
import { isLeaked, leakCount, MIN_NEW_PASSWORD, newPasswordProblem, sha1 } from '../../src/lib/pwned';

const range = (body: string, ok = true) => (async () => new Response(body, { status: ok ? 200 : 503 })) as unknown as typeof fetch;

describe('перевірка злитих паролів', () => {
  it('sha1 — великими літерами (відомий вектор)', async () => {
    expect(await sha1('password')).toBe('5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8');
  });
  it('збіг суфікса дає кількість, заповнювач і чужі — нуль', () => {
    const body = '0018A45C4D1DEF81644B54AB7F969B88D65:10\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:9545824\r\nFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:0';
    expect(leakCount(body, '1e4c9b93f3f0682250b6cf8331b7ee68fd8')).toBe(9545824);
    expect(leakCount(body, 'FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF')).toBe(0);
    expect(leakCount(body, 'AAAA')).toBe(0);
  });
  it('у мережу йдуть лише перші 5 символів хешу', async () => {
    let url = '';
    const f = (async (u: string) => { url = u; return new Response('1E4C9B93F3F0682250B6CF8331B7EE68FD8:3'); }) as unknown as typeof fetch;
    expect(await isLeaked('password', f)).toBe(true);
    expect(url).toBe('https://api.pwnedpasswords.com/range/5BAA6');
  });
  it('сервіс недоступний — не заважаємо (null)', async () => {
    expect(await isLeaked('password', range('', false))).toBeNull();
    expect(await isLeaked('password', (async () => { throw new Error('offline'); }) as unknown as typeof fetch)).toBeNull();
  });
  it('новий пароль: довжина, злитий, добрий', async () => {
    expect(MIN_NEW_PASSWORD).toBe(8);
    expect(await newPasswordProblem('abc1234', range(''))).toMatch(/від 8/);
    expect(await newPasswordProblem('password', range('1E4C9B93F3F0682250B6CF8331B7EE68FD8:3'))).toMatch(/витікав/);
    expect(await newPasswordProblem('Довгий-і-свій-2026', range('ABC:1'))).toBeNull();
  });
});
