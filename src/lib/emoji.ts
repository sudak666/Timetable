import { html, type TemplateResult } from 'lit-html';

/** 3D-емодзі Microsoft Fluent (MIT) лежать у public/assets/emoji; список збирається на етапі збірки. */
declare const __EMOJI__: string[];
const AVAILABLE = new Set(__EMOJI__);
const BASE = import.meta.env.BASE_URL;

const RX = /(?:[\u{1F1E6}-\u{1F1FF}]{2}|[☀-➿⬀-⯿\u{1F300}-\u{1FAFF}]️?(?:‍[☀-➿⬀-⯿\u{1F300}-\u{1FAFF}]️?)*)/gu;

export function emojiSrc(e: string): string | null {
  const cps = [...e].map((c) => c.codePointAt(0)!.toString(16));
  const full = cps.join('-'), bare = cps.filter((c) => c !== 'fe0f').join('-');
  const hit = AVAILABLE.has(full) ? full : AVAILABLE.has(bare) ? bare : null;
  return hit ? `${BASE}assets/emoji/${hit}.webp` : null;
}

/** Текст з емодзі → вузли lit-html з <img>. Текст завжди екранується lit-html. */
export function emo(text: string): (string | TemplateResult)[] {
  const out: (string | TemplateResult)[] = [];
  let last = 0;
  for (const m of text.matchAll(RX)) {
    const src = emojiSrc(m[0]);
    if (!src) continue;
    if (m.index! > last) out.push(text.slice(last, m.index));
    out.push(html`<img class="emo" src=${src} alt=${m[0]} draggable="false" decoding="async">`);
    last = m.index! + m[0].length;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}
