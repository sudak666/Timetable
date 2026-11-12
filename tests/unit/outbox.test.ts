import { describe, expect, it } from 'vitest';
import { enqueue, isRetryableStatus, overlay, type Op } from '../../src/features/outbox';

const hw = { id: 'h1', child_id: 'c', subject: 'Математика', due: '2026-10-16', text: 'ст. 5', done: false };
const ins: Op = { t: 'school_homework', kind: 'insert', row: { ...hw, family_id: 'f' } };

describe('outbox', () => {
  it('згортає оновлення у ще не відправлену вставку', () => {
    const q = enqueue([ins], { t: 'school_homework', kind: 'update', id: 'h1', patch: { done: true } });
    expect(q).toHaveLength(1);
    expect(q[0]!.kind === 'insert' && q[0]!.row).toMatchObject({ id: 'h1', done: true });
  });
  it('згортає повторні оновлення одного рядка', () => {
    let q = enqueue([], { t: 'school_homework', kind: 'update', id: 'x', patch: { done: true } });
    q = enqueue(q, { t: 'school_homework', kind: 'update', id: 'x', patch: { done: false } });
    expect(q).toEqual([{ t: 'school_homework', kind: 'update', id: 'x', patch: { done: false } }]);
  });
  it('overlay додає нові рядки без family_id і не дублює вже збережені', () => {
    const o = overlay([ins], [], []);
    expect(o.homework).toEqual([hw]);
    expect(overlay([ins], [], [hw]).homework).toHaveLength(1);
  });
  it('overlay застосовує оновлення', () => {
    const o = overlay([{ t: 'school_homework', kind: 'update', id: 'h1', patch: { done: true } }], [], [hw]);
    expect(o.homework[0]!.done).toBe(true);
  });
});

describe('isRetryableStatus', () => {
  it('збій сервера, ліміти й протермінований токен — лишаємо в черзі', () => {
    for (const s of [0, 401, 408, 425, 429, 500, 502, 503, 504]) expect(isRetryableStatus(s), String(s)).toBe(true);
  });
  it('свідома відмова — відкидаємо', () => {
    for (const s of [400, 403, 404, 409, 422]) expect(isRetryableStatus(s), String(s)).toBe(false);
  });
});
