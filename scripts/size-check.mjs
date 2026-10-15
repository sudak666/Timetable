import process from 'node:process';
import { readdirSync, readFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { join } from 'node:path';

// Бюджети (gzip, KB): початковий JS, CSS. Lazy-чанки рахуються окремо.
const BUDGET = { js: 40, css: 12 };
const dir = 'dist/assets';
const html = readFileSync('dist/index.html', 'utf8');
const initial = new Set([...html.matchAll(/assets\/([^"']+\.(?:js|css))/g)].map((m) => m[1]));
const sum = { js: 0, css: 0 };
for (const f of readdirSync(dir)) {
  const ext = f.endsWith('.js') ? 'js' : f.endsWith('.css') ? 'css' : null;
  if (!ext || !initial.has(f)) continue;
  sum[ext] += gzipSync(readFileSync(join(dir, f))).length / 1024;
}
let fail = false;
for (const k of /** @type {const} */ (['js', 'css'])) {
  const ok = sum[k] <= BUDGET[k];
  fail ||= !ok;
  console.log(`${ok ? '✓' : '✗'} ${k}: ${sum[k].toFixed(1)} KB / ${BUDGET[k]} KB`);
}
process.exit(fail ? 1 : 0);
