import { describe, expect, it } from 'vitest';
import { linkError } from '../../src/lib/authlink';

const SITE = 'https://sudak666.github.io/Timetable/';

describe('помилка входу в адресі', () => {
  it('використане або застаріле посилання з листа (як приходить від Supabase)', () => {
    const href = `${SITE}?error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired`
      + '#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired&sb=';
    expect(linkError(href)).toMatch(/вже використане або застаріло/);
    expect(linkError(`${SITE}#error_code=otp_expired`)).toMatch(/вже використане/);
  });
  it('інша помилка (наприклад, скасували вхід через Google) — загальний текст', () => {
    expect(linkError(`${SITE}?error=access_denied&error_description=User+cancelled`)).toMatch(/Не вдалося увійти/);
  });
  it('?code= з листа, запрошеного в Android-застосунку (PKCE), — пояснення, що робити', () => {
    const m = linkError(`${SITE}?code=0b1c2d3e-4f50-6172-8394-a5b6c7d8e9f0`);
    expect(m).toMatch(/просили в застосунку/);
    expect(m).toMatch(/Забули пароль\?/);
  });
  it('звичайні адреси й успішний вхід — без повідомлення', () => {
    expect(linkError(SITE)).toBeNull();
    expect(linkError(`${SITE}#grades?kid=1`)).toBeNull();
    expect(linkError(`${SITE}#access_token=x&type=recovery`)).toBeNull();
    expect(linkError('не адреса')).toBeNull();
  });
});
