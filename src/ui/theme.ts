import type { GameStyle } from '../state';

/** Ігрові шрифти вантажаться лише коли стиль обрано. */
export function applyStyle(s: GameStyle): void {
  if (s === 'arena') void import('@fontsource/russo-one/400.css');
  if (s === 'blocks') { void import('@fontsource/pixelify-sans/500.css'); void import('@fontsource/pixelify-sans/700.css'); }
  if (s) document.documentElement.dataset.style = s;
  else delete document.documentElement.dataset.style;
}
export function applyAccent(c: string | null | undefined): void {
  const r = document.documentElement.style;
  if (c && /^#[0-9a-f]{6}$/i.test(c)) { r.setProperty('--accent', c); r.setProperty('--glow', c + '40'); }
  else { r.removeProperty('--accent'); r.removeProperty('--glow'); }
}

