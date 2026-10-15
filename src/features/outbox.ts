import type { Grade, Homework } from './rewards';

/** Офлайн-черга змін. Вставки несуть клієнтський UUID → повтор ідемпотентний (upsert ignoreDuplicates). */
export type Op =
  | { t: 'school_grades'; kind: 'insert'; row: Grade & { family_id: string } }
  | { t: 'school_homework'; kind: 'insert'; row: Homework & { family_id: string } }
  | { t: 'school_homework'; kind: 'update'; id: string; patch: Partial<Pick<Homework, 'done'>> };

/** Додає операцію, згортаючи повторні зміни того самого рядка. */
export function enqueue(q: readonly Op[], op: Op): Op[] {
  if (op.kind === 'update') {
    const ins = q.findIndex((o) => o.kind === 'insert' && o.row.id === op.id);
    if (ins >= 0) {
      const o = q[ins]!;
      if (o.t !== 'school_homework' || o.kind !== 'insert') return [...q, op];
      return q.map((x, i) => (i === ins ? { ...o, row: { ...o.row, ...op.patch } } : x));
    }
    const upd = q.findIndex((o) => o.kind === 'update' && o.id === op.id);
    if (upd >= 0) {
      const o = q[upd] as Extract<Op, { kind: 'update' }>;
      return q.map((x, i) => (i === upd ? { ...o, patch: { ...o.patch, ...op.patch } } : x));
    }
  }
  return [...q, op];
}

/** Накладає чергу поверх даних сервера (оптимістичне відображення). */
export function overlay(q: readonly Op[], grades: Grade[], homework: Homework[]): { grades: Grade[]; homework: Homework[] } {
  let g = grades, h = homework;
  for (const o of q) {
    if (o.t === 'school_grades') {
      if (!g.some((x) => x.id === o.row.id)) g = [stripFamily(o.row), ...g];
    } else if (o.kind === 'insert') {
      if (!h.some((x) => x.id === o.row.id)) h = [...h, stripFamily(o.row)];
    } else h = h.map((x) => (x.id === o.id ? { ...x, ...o.patch } : x));
  }
  return { grades: g, homework: h };
}

function stripFamily<T extends { family_id: string }>(r: T): Omit<T, 'family_id'> {
  const { family_id, ...rest } = r;
  void family_id;
  return rest;
}

export const isOp = (x: unknown): x is Op =>
  !!x && typeof x === 'object' && 't' in x && 'kind' in x &&
  ((x as Op).t === 'school_grades' || (x as Op).t === 'school_homework');
