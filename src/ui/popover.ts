import { render, type TemplateResult } from 'lit-html';

let host: HTMLDivElement | null = null;
let anchorEl: HTMLElement | null = null;

export function closePop(): void {
  if (!host) return;
  render(null, host);
  host.remove();
  host = null;
  anchorEl?.setAttribute('aria-expanded', 'false');
  anchorEl?.focus();
  anchorEl = null;
}

/** Відкриває поповер під anchor. content отримує rerender для оновлення (напр., календар). */
export function openPop(anchor: HTMLElement, content: (rerender: () => void) => TemplateResult, label: string): void {
  const same = anchorEl === anchor;
  closePop();
  if (same) return;
  host = document.createElement('div');
  host.className = 'pop';
  host.setAttribute('role', 'dialog');
  host.setAttribute('aria-label', label);
  anchorEl = anchor;
  anchor.setAttribute('aria-expanded', 'true');
  document.body.append(host);
  const draw = () => host && render(content(draw), host);
  draw();
  const r = anchor.getBoundingClientRect();
  const w = host.offsetWidth;
  const x = Math.min(r.left + scrollX, scrollX + document.documentElement.clientWidth - w - 12);
  host.style.left = Math.max(12, x) + 'px';
  host.style.top = r.bottom + scrollY + 6 + 'px';
  (host.querySelector('.on, [aria-selected="true"], button') as HTMLElement | null)?.focus();
}

document.addEventListener('pointerdown', (e) => {
  const t = e.target as Node;
  if (host && !host.contains(t) && !anchorEl?.contains(t)) closePop();
});
document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && host) { e.preventDefault(); closePop(); } });
addEventListener('resize', () => host && closePop());
