/**
 * Пароль: лист «Забули пароль?», новий пароль після переходу з листа і зміна в «Профілі».
 * Лист надсилає Supabase Auth; посилання веде назад на сайт (implicit flow) — там AuthClient
 * піднімає сесію з адреси й надсилає подію PASSWORD_RECOVERY (див. watchAuth в api.ts).
 */
import { errText, sb } from '../api';
import { MIN_NEW_PASSWORD, newPasswordProblem } from '../lib/pwned';
import { alert, prompt } from '../ui/dialog';
import { toast } from '../ui/toast';

export const MIN_PASSWORD = MIN_NEW_PASSWORD;

/** Куди веде посилання з листа — та сама сторінка, звідки його попросили. */
const redirect = () => location.origin + location.pathname;

const looksLikeEmail = (s: string) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(s);

/** «Забули пароль?»: лист на пошту. Відповідь однакова, чи є такий акаунт (щоб не видавати, хто зареєстрований). */
export async function forgotPassword(prefill: string): Promise<void> {
  const email = (prefill.trim() || (await prompt('На яку пошту надіслати посилання для нового пароля?', '', 'text')) || '').trim();
  if (!email) return;
  if (!looksLikeEmail(email)) return void toast('Схоже, у пошті помилка', 'err');
  const { error } = await sb.auth.resetPasswordForEmail(email, { redirectTo: redirect() });
  if (error && !/rate|limit|seconds/i.test(error.message)) return void toast(errText(error), 'err');
  await alert(error
    ? 'Лист уже надсилали щойно — зачекай хвилину й перевір пошту 📬'
    : `Якщо акаунт ${email} існує, ми надіслали на нього лист. Відкрий посилання з листа — і задай новий пароль 📬`);
}

/** Новий пароль двічі (щоб не помилитися) і збереження. true — змінено. */
export async function changePassword(title = `Новий пароль (від ${MIN_PASSWORD} символів)`): Promise<boolean> {
  const p1 = await prompt(title, '', 'password');
  if (p1 === null) return false;
  const bad = await newPasswordProblem(p1);
  if (bad) return void toast(bad, 'err'), false;
  const p2 = await prompt('Повтори новий пароль', '', 'password');
  if (p2 === null) return false;
  if (p1 !== p2) return void toast('Паролі не збігаються — спробуй ще раз', 'err'), false;
  const { error } = await sb.auth.updateUser({ password: p1 });
  if (error) {
    const m = /different from the old|same/i.test(error.message) ? 'Новий пароль має відрізнятися від старого' : errText(error);
    toast(m, 'err');
    return false;
  }
  toast('Пароль змінено 🔑');
  return true;
}

/** Перехід із листа відновлення: сесія вже є — одразу просимо новий пароль. */
export async function finishRecovery(): Promise<void> {
  history.replaceState(null, '', location.pathname + location.search);
  await changePassword(`Задай новий пароль (від ${MIN_PASSWORD} символів)`);
}
