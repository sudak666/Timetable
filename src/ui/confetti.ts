const reduce = matchMedia('(prefers-reduced-motion: reduce)');
let cv: HTMLCanvasElement | null = null;
let ctx: CanvasRenderingContext2D | null = null;
interface P { x: number; y: number; vx: number; vy: number; r: number; c: string; a: number; life: number }
let parts: P[] = [];

function tick() {
  if (!cv || !ctx) return;
  const dpr = devicePixelRatio || 1;
  if (cv.width !== innerWidth * dpr) { cv.width = innerWidth * dpr; cv.height = innerHeight * dpr; }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, innerWidth, innerHeight);
  parts = parts.filter((p) => p.life-- > 0);
  for (const p of parts) {
    p.vy += 0.35; p.x += p.vx; p.y += p.vy; p.a += 0.2;
    ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.a); ctx.fillStyle = p.c;
    ctx.fillRect(-p.r / 2, -p.r / 2, p.r, p.r * 1.6); ctx.restore();
  }
  if (parts.length) requestAnimationFrame(tick);
}

export function boom(x = innerWidth / 2, y = innerHeight / 3, n = 120): void {
  if (reduce.matches) return;
  if (!cv) {
    cv = document.createElement('canvas');
    cv.className = 'confetti';
    cv.setAttribute('aria-hidden', 'true');
    document.body.append(cv);
    ctx = cv.getContext('2d');
  }
  const idle = !parts.length;
  for (let i = 0; i < n; i++) parts.push({ x, y, vx: (Math.random() - 0.5) * 12, vy: -Math.random() * 12 - 2, r: Math.random() * 6 + 3, c: `hsl(${Math.random() * 360} 90% 60%)`, a: Math.random() * 6, life: 90 + Math.random() * 40 });
  if (idle) requestAnimationFrame(tick);
}
