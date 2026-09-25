import { html, render } from 'lit-html';
import { classMap } from 'lit-html/directives/class-map.js';
import { emo } from '../lib/emoji';

type Kind = 'ok' | 'err' | 'info';
interface T { id: number; text: string; kind: Kind }
let items: T[] = [];
let seq = 0;
let host: HTMLElement | null = null;

function draw() {
  if (!host) {
    host = document.createElement('div');
    host.className = 'toasts';
    document.body.append(host);
  }
  // Дві live-області: помилки оголошуються негайно (assertive), решта — ввічливо.
  render(html`<div role="status" aria-live="polite">${items.filter((t) => t.kind !== 'err').map(view)}</div>
    <div role="alert" aria-live="assertive">${items.filter((t) => t.kind === 'err').map(view)}</div>`, host);
}
const view = (t: T) => html`<div class=${classMap({ toast: true, [t.kind]: true })}>
  <span>${emo(t.text)}</span><button type="button" class="x" aria-label="Закрити" @click=${() => dismiss(t.id)}>✕</button></div>`;

function dismiss(id: number) {
  items = items.filter((t) => t.id !== id);
  draw();
}

/** Неблокуюче повідомлення (замість модального alert для результатів дій). */
export function toast(text: string, kind: Kind = 'ok', ms = kind === 'err' ? 6000 : 3000): void {
  const id = ++seq;
  items = [...items.slice(-2), { id, text, kind }];
  draw();
  setTimeout(() => dismiss(id), ms);
}
