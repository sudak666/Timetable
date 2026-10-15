/** Нечіткий пошук: усі символи запиту в порядку; бал — компактність збігу і збіг з початку слова. */
export function fuzzyScore(query: string, text: string): number {
  const q = query.toLowerCase().trim(), t = text.toLowerCase();
  if (!q) return 1;
  if (t.includes(q)) return 100 - t.indexOf(q) + (t.indexOf(q) === 0 || t[t.indexOf(q) - 1] === ' ' ? 50 : 0);
  let i = 0, first = -1, last = -1;
  for (let j = 0; j < t.length && i < q.length; j++) {
    if (t[j] === q[i]) { if (first < 0) first = j; last = j; i++; }
  }
  return i === q.length ? Math.max(1, 50 - (last - first)) : 0;
}

export interface QuickGrade { grade: number; subject: string; offset: number }

/**
 * «10 матем», «зар англ вчора», «математика 11 позавчора» → оцінка, предмет, зсув днів.
 * Предмет — за префіксом будь-якого слова назви (≥ 3 літери), однозначно.
 */
export function parseQuickGrade(input: string, subjects: readonly string[], pass: number): QuickGrade | null {
  const words = input.toLowerCase().replace(/[,.]/g, ' ').split(/\s+/).filter(Boolean);
  let grade: number | null = null, offset = 0;
  const rest: string[] = [];
  for (const w of words) {
    if (/^\d{1,2}$/.test(w) && +w >= 1 && +w <= 12 && grade === null) grade = +w;
    else if (/^зар/.test(w) && grade === null) grade = pass;
    else if (w === 'вчора') offset = -1;
    else if (w === 'позавчора') offset = -2;
    else if (w === 'сьогодні') offset = 0;
    else if (w !== 'з' && w !== 'із') rest.push(w);
  }
  if (grade === null || !rest.length) return null;
  const stem = rest.join(' ');
  if (stem.length < 3) return null;
  const hits = subjects.filter((s) => { const l = s.toLowerCase(); return l.startsWith(stem) || l.split(/[\s.-]+/).some((p) => p.startsWith(stem)); });
  return hits.length === 1 ? { grade, subject: hits[0]!, offset } : null;
}
