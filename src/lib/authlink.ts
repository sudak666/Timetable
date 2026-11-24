/**
 * Посилання з листа (відновлення пароля, підтвердження пошти) або вхід через Google не вдався —
 * Supabase повертає на сайт з помилкою в адресі (?error=…#error_code=…). Без цього сайт мовчки
 * показував звичайну сторінку, а помилка лишалась в адресі.
 */

/** Текст для людини або null, якщо в адресі немає помилки входу. */
export function linkError(href: string): string | null {
  let u: URL;
  try {
    u = new URL(href);
  } catch {
    return null;
  }
  const hash = new URLSearchParams(u.hash.replace(/^#/, ''));
  const get = (k: string) => hash.get(k) ?? u.searchParams.get(k);
  if (!get('error') && !get('error_code') && !get('error_description')) return null;
  if (get('error_code') === 'otp_expired') {
    return 'Посилання з листа вже використане або застаріло — воно спрацьовує лише один раз. '
      + 'Якщо ти вже увійшов — усе гаразд. Якщо ні — попроси новий лист 📬';
  }
  return 'Не вдалося увійти за цим посиланням. Спробуй ще раз 🙏';
}
