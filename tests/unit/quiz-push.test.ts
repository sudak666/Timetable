import { describe, expect, it } from 'vitest';
import { quizParentPush } from '../../supabase/functions/school-push/quiz.ts';

const a = (correct: boolean, paid = 0) => ({ correct, paid });

describe('сповіщення батькам про вікторину', () => {
  it('після кожного 3-го питання — підсумок дня', () => {
    expect(quizParentPush('Тарас', [a(true, 20), a(false), a(true, 20)])).toEqual({
      title: '🧠 Тарас: вікторина — 2 з 3 правильно',
      body: 'Сьогодні зароблено +40 ₴. 3 відповіді — на головній у картці «Вікторина»',
    });
  });

  it('посеред раунду — тиша', () => {
    expect(quizParentPush('Тарас', [])).toBeNull();
    expect(quizParentPush('Тарас', [a(true, 20)])).toBeNull();
    expect(quizParentPush('Тарас', [a(true, 20), a(true, 20), a(true, 20), a(false)])).toBeNull();
  });

  it('без грошей — без суми; відмінювання «відповідей»', () => {
    const six = [a(false), a(false), a(false), a(true), a(false), a(false)];
    expect(quizParentPush('Оля', six)).toEqual({
      title: '🧠 Оля: вікторина — 1 з 6 правильно',
      body: '6 відповідей — на головній у картці «Вікторина»',
    });
    expect(quizParentPush('Оля', Array.from({ length: 21 }, () => a(true)))?.body).toBe('21 відповідь — на головній у картці «Вікторина»');
    expect(quizParentPush('Оля', Array.from({ length: 12 }, () => a(true)))?.body).toBe('12 відповідей — на головній у картці «Вікторина»');
    expect(quizParentPush('Оля', Array.from({ length: 24 }, () => a(true)))?.body).toBe('24 відповіді — на головній у картці «Вікторина»');
  });
});
