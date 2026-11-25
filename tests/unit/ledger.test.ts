import { describe, expect, it } from 'vitest';
import { ledgerPush } from '../../supabase/functions/school-push/ledger.ts';

describe('сповіщення про гаманець', () => {
  it('зірка за поведінку', () => {
    expect(ledgerPush({ kind: 'bonus', amount: 50, note: '⭐ За поведінку', src: 'star' })).toEqual({ title: '⭐ Зірка за поведінку: +50 ₴', body: 'Так тримати! 💪' });
  });
  it('вікторина — без сповіщення (дитина щойно відповіла сама)', () => {
    expect(ledgerPush({ kind: 'bonus', amount: 20, note: '🧠 Вікторина: …', src: 'quiz' })).toBeNull();
  });
  it('бонус, штраф і виплата — як раніше', () => {
    expect(ledgerPush({ kind: 'bonus', amount: 100, note: 'Молодець' })).toEqual({ title: '🎁 Бонус +100 ₴', body: 'Молодець' });
    expect(ledgerPush({ kind: 'bonus', amount: -30, note: null, src: null })).toEqual({ title: '⚠️ Штраф -30 ₴', body: '' });
    expect(ledgerPush({ kind: 'payout', amount: 400, note: 'Кіно' })).toEqual({ title: '💸 Виплачено 400 ₴', body: 'Кіно' });
  });
});
