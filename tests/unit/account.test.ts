import { describe, expect, it } from 'vitest';
import { deletionWarnings, isDeleteConfirmed } from '../../src/features/account';
import type { Member } from '../../src/features/rewards';

const m = (user_id: string, role: Member['role'], name: string): Member =>
  ({ user_id, role, name, goal_title: '', goal_amount: 0, avatar: '', theme: '' });
const mom = m('p1', 'parent', 'Мама'), dad = m('p2', 'parent', 'Тато'), kid = m('k1', 'child', 'Сашко');

describe('видалення акаунта: попередження', () => {
  it('останній з батьків — уся сім’я й діти', () => {
    const w = deletionWarnings(true, [mom, kid], 'p1', 'Сім’я');
    expect(w.some((x) => x.includes('повністю'))).toBe(true);
    expect(w.some((x) => x.includes('Сашко'))).toBe(true);
  });
  it('є другий з батьків — сім’я лишається', () => {
    const w = deletionWarnings(true, [mom, dad, kid], 'p1', 'Сім’я');
    expect(w.some((x) => x.includes('лишиться другому'))).toBe(true);
    expect(w.some((x) => x.includes('повністю'))).toBe(false);
  });
  it('дитина — лише свої дані', () => {
    expect(deletionWarnings(false, [mom, kid], 'k1', 'Сім’я')[1]).toContain('Усі твої оцінки');
  });
});

describe('слово-підтвердження', () => {
  it.each([['ВИДАЛИТИ', true], [' видалити ', true], ['видалити б', false], ['', false], [null, false]] as const)('%s → %s', (v, ok) =>
    expect(isDeleteConfirmed(v)).toBe(ok));
});
