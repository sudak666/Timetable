import { html, nothing, render, type TemplateResult } from 'lit-html';
import { regionItems, shortName, topLevel, type AlertLoc } from '../features/alerts';
import { emo } from '../lib/emoji';
import { state } from '../state';
import { isBackdropClick } from '../ui/backdrop';

const SOURCE = 'Дані alerts.in.ua з затримкою до хвилини. Не замінює офіційні застосунки та сирени.';

/** Картка «Повітряні тривоги» в профілі: батьки обирають місце, дитина — бачить. */
export function alertCard(choose: () => void, clear: () => void): TemplateResult {
  const par = state.role === 'parent', place = state.air?.name ?? (state.family?.alert_uid ? '…' : null);
  return html`<section class="side card" aria-labelledby="air-h"><h2 class="h4" id="air-h">Повітряні тривоги</h2>
    <p class="air-place">${place ? emo('📍 ' + place) : par ? 'Місце не обрано — оберіть, де навчаються діти, і тут з’явиться плашка тривоги.' : 'Місце для тривог обирають батьки.'}</p>
    ${par ? html`<div class="acts"><button type="button" @click=${choose}>${emo(place ? '📍 Змінити місце' : '📍 Обрати місце')}</button>
      ${place ? html`<button type="button" @click=${clear}>Прибрати місце</button>` : nothing}</div>` : nothing}
    <p class="msg">${SOURCE}</p>
    <p class="msg">Сповіщення про тривогу на телефон — у застосунку «Дванадцятка» для Android.</p>
  </section>`;
}

/**
 * Вибір місця: область чи окреме місто → (для області) уся область, район або громада, з пошуком.
 * Повертає uid або undefined, якщо скасували. load — довідник (null — області й міста).
 */
export function pickRegion(load: (oblast: number | null) => Promise<AlertLoc[]>): Promise<number | undefined> {
  return new Promise((resolve) => {
    const prev = document.activeElement as HTMLElement | null;
    const d = document.createElement('dialog');
    d.className = 'md rg';
    d.setAttribute('aria-labelledby', 'rg-t');
    let oblast: AlertLoc | null = null, items: AlertLoc[] | null = null, err = '', query = '';
    const close = (v: number | undefined) => { d.close(); d.remove(); prev?.focus?.(); resolve(v); };
    const open = async (o: AlertLoc | null) => {
      oblast = o; items = null; err = ''; query = ''; draw();
      try { items = await load(o ? o.uid : null); } catch { err = 'Не вдалося завантажити список. Перевірте інтернет і спробуйте ще раз.'; }
      draw();
      (d.querySelector(o ? 'input' : '.rg-list button') as HTMLElement | null)?.focus();
    };
    // Назва кнопки — її видимий текст (WCAG 2.5.3): так її знайде й голосове керування.
    const row = (label: unknown, sub: string | null, cls: string, onClick: () => void) =>
      html`<li><button type="button" class=${'rg-o ' + cls} @click=${onClick}><span>${label}${sub ? html`<small>${sub}</small>` : nothing}</span>${cls.includes('go') ? html`<b aria-hidden="true">›</b>` : nothing}</button></li>`;
    const list = (): TemplateResult => {
      if (err) return html`<p class="msg neg" role="alert">${err}</p><button type="button" @click=${() => void open(oblast)}>Ще раз</button>`;
      if (!items) return html`<p class="msg" role="status" aria-busy="true">Завантажую…</p>`;
      if (!oblast) {
        return html`<ul class="rg-list">${topLevel(items).map((l) => l.kind === 'city'
          ? row(l.name, 'Тривоги оголошують окремо від області', '', () => close(l.uid))
          : row(l.name, null, 'go', () => void open(l)))}</ul>`;
      }
      const found = regionItems(items, query), o = oblast;
      return html`<input type="search" placeholder="Пошук громади чи району" aria-label="Пошук громади чи району" .value=${query}
          @input=${(e: Event) => { query = (e.target as HTMLInputElement).value; draw(); }}>
        <ul class="rg-list">
          ${row(emo('🗺️ Уся область'), 'Тривога — коли вона в усій області, «частково» — коли лише в деяких громадах', 'all', () => close(o.uid))}
          ${found.map((i) => i.raion
            ? row(i.loc.name, 'Увесь район', 'raion', () => close(i.loc.uid))
            : row(shortName(i.loc.name), null, 'hrom', () => close(i.loc.uid)))}
        </ul>${found.length ? nothing : html`<p class="msg" role="status">Нічого не знайдено</p>`}`;
    };
    const draw = () => render(html`<div class="rg-h">
        ${oblast ? html`<button type="button" class="ic" aria-label="Назад до областей" @click=${() => void open(null)}>‹</button>` : nothing}
        <p id="rg-t">${oblast ? oblast.name : 'Місце для тривог'}</p></div>
      ${oblast ? nothing : html`<p class="msg">Оберіть область, а далі — район чи громаду, де навчаються діти. Точніше місце — менше зайвих тривог.</p>`}
      ${list()}
      <div class="row"><button type="button" @click=${() => close(undefined)}>Скасувати</button></div>`, d);
    d.addEventListener('cancel', (e) => { e.preventDefault(); close(undefined); });
    d.addEventListener('click', (e) => { if (isBackdropClick(d, e)) close(undefined); });
    document.body.append(d);
    d.showModal();
    void open(null);
  });
}
