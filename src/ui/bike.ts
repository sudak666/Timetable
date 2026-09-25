import { html, svg } from 'lit-html';
import { boom } from './confetti';

/** Уся анімація — CSS transform/opacity на GPU (compositor-only), без JS-циклу по кадрах. */
const wheel = (x: number, front: boolean) => svg`<g transform="translate(${x} 58)">
  <circle r="15.5" fill="#111827"/><circle r="11" fill="none" stroke="#4b5563" stroke-width="1.2"/>
  <g class="bk-spin"><path d="M-10 0H10M0-10V10M-7-7L7 7M-7 7L7-7" stroke="#9ca3af" stroke-width="1.3"/><circle r="3.2" fill="#d1d5db"/>${front ? svg`<circle r="6" fill="none" stroke="#ef4444" stroke-width="1.2"/>` : null}</g>
  <circle r="15.5" fill="none" stroke="#000" stroke-opacity=".35" stroke-width="3" stroke-dasharray="2.4 2.4"/></g>`;

const bikeSvg = svg`<svg viewBox="0 0 130 78" width="156" height="94" aria-hidden="true" focusable="false">
  <defs>
    <linearGradient id="bkBody" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#22d3ee"/><stop offset=".55" class="bk-acc"/><stop offset="1" stop-color="#312e81"/></linearGradient>
    <linearGradient id="bkMetal" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#e5e7eb"/><stop offset="1" stop-color="#6b7280"/></linearGradient>
    <radialGradient id="bkLight"><stop offset="0" stop-color="#fff9c4"/><stop offset="1" stop-color="#fde047" stop-opacity="0"/></radialGradient>
  </defs>
  <g class="bk-body">
    <path d="M104 26 L128 18 L128 36 Z" fill="url(#bkLight)" opacity=".75"/>
    <path d="M28 55 L60 47" stroke="#374151" stroke-width="5" stroke-linecap="round"/>
    <path d="M28 55 L60 47" stroke="#9ca3af" stroke-width="1.6" stroke-linecap="round"/>
    <circle cx="60" cy="47" r="6.5" fill="#1f2937"/><circle cx="60" cy="47" r="3" fill="#22d3ee"/>
    <path d="M84 20 L102 57" stroke="#1f2937" stroke-width="6" stroke-linecap="round"/>
    <path d="M85 23 L96 45" stroke="url(#bkMetal)" stroke-width="3.2" stroke-linecap="round"/>
    <path d="M46 33 L83 25 Q86 25 85 29 L78 48 Q76 51 72 51 L54 52 Q49 52 48 48 Z" fill="url(#bkBody)"/>
    <path d="M50 36 L80 30" stroke="#fff" stroke-opacity=".35" stroke-width="1.6" stroke-linecap="round"/>
    <path d="M66 35 l-4 7 h4 l-2 6 l7-9 h-4 l2-4 z" fill="#fff" opacity=".95"/>
    <path d="M32 28 Q52 20 76 22 Q79 23 77 26 Q55 25 36 32 Q31 33 32 28 Z" fill="#111827"/>
    <path d="M34 31 Q24 30 13 37 Q12 40 15 40 Q25 35 38 35 Z" fill="url(#bkBody)"/>
    <path d="M90 34 Q101 30 110 36 Q111 39 108 39 Q100 35 92 38 Z" fill="url(#bkBody)"/>
    <path d="M83 22 L82 12" stroke="#374151" stroke-width="3" stroke-linecap="round"/>
    <path d="M77 12 L90 10" stroke="#111827" stroke-width="3.4" stroke-linecap="round"/>
    <circle cx="89" cy="21" r="4.2" fill="#111827"/><circle cx="90" cy="21" r="2.6" fill="#fde047"/>
    <path d="M40 44 L48 44" stroke="#22d3ee" stroke-width="2" stroke-linecap="round" class="bk-glow"/>
  </g>
  ${wheel(28, false)}${wheel(102, true)}
</svg>`;

function wheelie(e: Event) {
  const el = e.currentTarget as HTMLElement;
  el.classList.remove('pop-wheelie');
  void el.offsetWidth; // перезапуск CSS-анімації
  el.classList.add('pop-wheelie');
  const r = el.getBoundingClientRect();
  boom(r.left + r.width / 2, r.top + r.height / 2, 40);
}

export const bikeLane = (label: unknown) => html`
  <div class="lane">
    <div class="lane-txt">${label}</div>
    <div class="bike-track"><div class="bike-dir">
      <button class="bike" type="button" @click=${wheelie} aria-label="Електробайк: натисни, щоб зробити вілі">
        <span class="bk-sh"></span>
        <span class="bk-dust" aria-hidden="true"><i></i><i></i><i></i></span>
        <span class="bk-in">${bikeSvg}</span>
      </button>
    </div></div>
  </div>`;
