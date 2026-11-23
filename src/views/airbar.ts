import { html, nothing, type TemplateResult } from 'lit-html';
import { classMap } from 'lit-html/directives/class-map.js';
import { alertBanner } from '../features/alerts';
import { emo } from '../lib/emoji';
import { state } from '../state';

/**
 * Плашка тривоги над вкладками (як в Android). Тривога — кольором попередження, «частково» — спокійніше.
 * Живий регіон — лише заголовок: «станом на» оновлюється щохвилини, і читач екрана не повторює його щоразу.
 */
export function alertBar(): TemplateResult | typeof nothing {
  const b = alertBanner(state.air, state.now);
  if (!b) return nothing;
  return html`<div class=${classMap({ air: true, on: b.active })}>
    <p class="air-t" role="status">${emo(b.title)}</p>${b.detail ? html`<p class="air-d">${b.detail}</p>` : nothing}</div>`;
}
