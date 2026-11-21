export function applyAccent(c: string | null | undefined): void {
  const r = document.documentElement.style;
  if (c && /^#[0-9a-f]{6}$/i.test(c)) { r.setProperty('--accent', c); r.setProperty('--glow', c + '40'); }
  else { r.removeProperty('--accent'); r.removeProperty('--glow'); }
}

