import { defineConfig } from 'vitest/config';
import type { Plugin } from 'vite';
import { readdirSync } from 'node:fs';

const EMOJI = readdirSync('public/assets/emoji').filter((f) => f.endsWith('.webp')).map((f) => f.slice(0, -5));

const SB = 'https://vkwkyhjjjmcpmiakxohw.supabase.co';
// CSP лише у production-збірці: dev-сервер Vite використовує inline-скрипти для HMR.
const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https://*.googleusercontent.com",
  "font-src 'self'",
  `connect-src 'self' ${SB} ${SB.replace('https', 'wss')}`,
  "worker-src 'self'",
  "manifest-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ');

const csp = (): Plugin => ({
  name: 'csp',
  apply: 'build',
  transformIndexHtml: (html) =>
    html.replace('<meta charset="UTF-8">', `<meta charset="UTF-8">\n<meta http-equiv="Content-Security-Policy" content="${CSP}">`),
});

export default defineConfig({
  root: 'app',
  publicDir: '../public',
  base: './',
  plugins: [csp()],
  define: { __EMOJI__: JSON.stringify(EMOJI) },
  build: { target: 'es2022', sourcemap: true, assetsInlineLimit: 0, outDir: '../dist', emptyOutDir: true },
  test: { root: '.', include: ['tests/unit/**/*.test.ts'], environment: 'node' },
});
