import { html, render } from 'lit-html';
import { emo } from '../lib/emoji';
import { fuzzyScore } from '../lib/command';

export interface Cmd { id: string; label: string; hint?: string; keys?: string; run: () => unknown }

let open = false;

/** Command Palette на нативному <dialog> (пастка фокуса, Esc), ARIA combobox + listbox. */
export function openPalette(base: () => Cmd[], dynamic: (q: string) => Cmd[] = () => []): void {
  if (open) return;
  open = true;
  const prev = document.activeElement as HTMLElement | null;
  const d = document.createElement('dialog');
  d.className = 'md cmdk';
  d.setAttribute('aria-label', 'Команди');
  let q = '', sel = 0, list: Cmd[] = [];

  const close = () => { d.close(); d.remove(); open = false; prev?.focus?.(); };
  const exec = (c: Cmd | undefined) => { if (!c) return; close(); void c.run(); };
  const filter = () => {
    const scored = base().map((c) => ({ c, s: fuzzyScore(q, c.label + ' ' + (c.hint ?? '')) })).filter((x) => x.s > 0);
    scored.sort((a, b) => b.s - a.s);
    list = [...dynamic(q), ...scored.map((x) => x.c)].slice(0, 8);
    sel = Math.min(sel, Math.max(0, list.length - 1));
  };
  const draw = () => render(html`
    <input role="combobox" aria-expanded="true" aria-controls="cmdk-l" aria-autocomplete="list" aria-label="Команда або «10 математика»"
      aria-activedescendant=${list.length ? `cmdk-${sel}` : ''} placeholder="Куди перейти або «10 математика вчора»…" autocomplete="off" spellcheck="false"
      @input=${(e: Event) => { q = (e.target as HTMLInputElement).value; sel = 0; filter(); draw(); }}
      @keydown=${(e: KeyboardEvent) => {
        if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
          e.preventDefault();
          if (list.length) sel = (sel + (e.key === 'ArrowDown' ? 1 : list.length - 1)) % list.length;
          draw();
          d.querySelector(`#cmdk-${sel}`)?.scrollIntoView({ block: 'nearest' });
        } else if (e.key === 'Enter') { e.preventDefault(); exec(list[sel]); }
      }}>
    <ul id="cmdk-l" role="listbox" aria-label="Результати">${list.length ? list.map((c, i) => html`
      <li id=${`cmdk-${i}`} role="option" aria-selected=${i === sel} class=${i === sel ? 'on' : ''}
        @pointermove=${() => { if (sel !== i) { sel = i; draw(); } }} @click=${() => exec(c)}>
        <span>${emo(c.label)}</span>${c.keys ? html`<kbd>${c.keys}</kbd>` : c.hint ? html`<small>${c.hint}</small>` : ''}</li>`)
      : html`<li class="msg" role="presentation">Нічого не знайдено</li>`}</ul>`, d);

  d.addEventListener('cancel', (e) => { e.preventDefault(); close(); });
  d.addEventListener('click', (e) => { if (e.target === d) close(); });
  filter();
  draw();
  document.body.append(d);
  d.showModal();
  d.querySelector('input')!.focus();
}
