import { html, render } from 'lit-html';
import { emo } from '../lib/emoji';
import { isBackdropClick } from './backdrop';

interface Opts {
  input?: 'text' | 'number' | 'password'; value?: string | number; ok?: string; cancel?: string;
  /** Для нового пароля: чий він. Без цього менеджер паролів вгадує акаунт і пропонує перезаписати чужий (24.11.2026). */
  username?: string | undefined;
}

/**
 * Модальне вікно на нативному <dialog>: пастка фокуса, Esc, aria-modal і повернення фокуса — від браузера.
 * Повертає введене значення / true, або null / false при скасуванні.
 */
export function modal(text: string, o: Opts = {}): Promise<string | boolean | null> {
  return new Promise((resolve) => {
    const prev = document.activeElement as HTMLElement | null;
    const d = document.createElement('dialog');
    d.className = 'md';
    d.setAttribute('aria-labelledby', 'md-t');
    const cancelVal = o.input ? null : false;
    const close = (v: string | boolean | null) => {
      d.close();
      d.remove();
      prev?.focus?.();
      resolve(v);
    };
    const submit = (e: Event) => {
      e.preventDefault();
      close(o.input ? (d.querySelector('input:not([hidden])') as HTMLInputElement).value : true);
    };
    render(
      html`<form method="dialog" @submit=${submit}>
        <p id="md-t">${emo(text)}</p>
        ${o.username ? html`<input type="email" name="username" autocomplete="username" .value=${o.username} readonly hidden>` : null}
        ${o.input
          ? html`<input type=${o.input} inputmode=${o.input === 'number' ? 'numeric' : 'text'} maxlength=${o.input === 'password' ? 72 : 80} .value=${String(o.value ?? '')} aria-labelledby="md-t" autocomplete=${o.input === 'password' ? 'new-password' : 'off'}>`
          : null}
        <div class="row">
          ${o.cancel ? html`<button type="button" @click=${() => close(cancelVal)}>${o.cancel}</button>` : null}
          <button class="pri" type="submit">${o.ok ?? 'OK'}</button>
        </div>
      </form>`,
      d,
    );
    d.addEventListener('cancel', (e) => { e.preventDefault(); close(cancelVal); });
    // Вікно з полем введення дотиком повз нього не закриваємо: випадковий дотик (напр., щоб сховати
    // клавіатуру) не має губити введене. Для них є «Скасувати» і «назад».
    d.addEventListener('click', (e) => { if (!o.input && isBackdropClick(d, e)) close(cancelVal); });
    document.body.append(d);
    d.showModal();
    const inp = d.querySelector<HTMLInputElement>('input:not([hidden])');
    if (inp) { inp.focus(); inp.select(); }
  });
}

export const alert = (t: string) => modal(t, { ok: 'Зрозуміло' });
export const confirm = async (t: string, ok = 'Так') => (await modal(t, { ok, cancel: 'Скасувати' })) === true;
export const prompt = async (t: string, value: string | number = '', input: 'text' | 'number' | 'password' = 'number', username?: string) => {
  const v = await modal(t, { input, value, ok: 'Готово', cancel: 'Скасувати', username });
  return typeof v === 'string' ? v : null;
};
