import { html, render } from 'lit-html';
import { emo } from '../lib/emoji';

interface Opts { input?: 'text' | 'number'; value?: string | number; ok?: string; cancel?: string }

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
      close(o.input ? (d.querySelector('input') as HTMLInputElement).value : true);
    };
    render(
      html`<form method="dialog" @submit=${submit}>
        <p id="md-t">${emo(text)}</p>
        ${o.input
          ? html`<input type=${o.input} inputmode=${o.input === 'number' ? 'numeric' : 'text'} maxlength="80" .value=${String(o.value ?? '')} aria-labelledby="md-t" autocomplete="off">`
          : null}
        <div class="row">
          ${o.cancel ? html`<button type="button" @click=${() => close(cancelVal)}>${o.cancel}</button>` : null}
          <button class="pri" type="submit">${o.ok ?? 'OK'}</button>
        </div>
      </form>`,
      d,
    );
    d.addEventListener('cancel', (e) => { e.preventDefault(); close(cancelVal); });
    d.addEventListener('click', (e) => { if (e.target === d) close(cancelVal); });
    document.body.append(d);
    d.showModal();
    const inp = d.querySelector('input');
    if (inp) { inp.focus(); inp.select(); }
  });
}

export const alert = (t: string) => modal(t, { ok: 'Зрозуміло' });
export const confirm = async (t: string) => (await modal(t, { ok: 'Так', cancel: 'Скасувати' })) === true;
export const prompt = async (t: string, value: string | number = '', input: 'text' | 'number' = 'number') => {
  const v = await modal(t, { input, value, ok: 'Готово', cancel: 'Скасувати' });
  return typeof v === 'string' ? v : null;
};
