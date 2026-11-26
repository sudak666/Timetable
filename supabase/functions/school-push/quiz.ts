/**
 * Сповіщення батькам про вікторину дитини. Чиста логіка без мережі — тести у vitest (tests/unit/quiz-push.test.ts).
 * Сповіщаємо раз на раунд: коли за день набралося 3, 6, 9… відповідей (а не після кожного натискання).
 */
export interface QuizAnswerRow { correct: boolean; paid: number }

export const QUIZ_ROUND = 3;

/** Скільки відповідей «відповідь/відповіді/відповідей». */
function answersWord(n: number): string {
  const d = n % 10, h = n % 100;
  if (d === 1 && h !== 11) return "відповідь";
  if (d >= 2 && d <= 4 && (h < 12 || h > 14)) return "відповіді";
  return "відповідей";
}

export function quizParentPush(name: string, today: QuizAnswerRow[]): { title: string; body: string } | null {
  const n = today.length;
  if (!n || n % QUIZ_ROUND !== 0) return null;
  const ok = today.filter((a) => a.correct).length;
  const paid = today.reduce((s, a) => s + Math.max(0, a.paid), 0);
  return {
    title: `🧠 ${name}: вікторина — ${ok} з ${n} правильно`,
    body: (paid > 0 ? `Сьогодні зароблено +${paid} ₴. ` : "") + `${n} ${answersWord(n)} — на головній у картці «Вікторина»`,
  };
}
