import { describe, expect, it } from 'vitest';
import { fuzzyScore, parseQuickGrade } from '../../src/lib/command';
import { SUBJECT_LIST } from '../../src/data/schedule';

describe('fuzzyScore', () => {
  it('підрядок з початку слова важить більше за розкиданий збіг', () => {
    expect(fuzzyScore('дом', 'Домашка')).toBeGreaterThan(fuzzyScore('дмк', 'Домашка'));
    expect(fuzzyScore('xyz', 'Домашка')).toBe(0);
    expect(fuzzyScore('', 'будь-що')).toBe(1);
  });
});

describe('parseQuickGrade', () => {
  const S = SUBJECT_LIST as readonly string[];
  it('розбирає оцінку, предмет і день', () => {
    const r = parseQuickGrade('10 матем вчора', S, 0);
    expect(r).toEqual({ grade: 10, subject: 'Математика', offset: -1 });
  });
  it('«зар» → залік', () => {
    expect(parseQuickGrade('зар матем', S, 0)?.grade).toBe(0);
  });
  it('без оцінки, з неоднозначним чи коротким предметом → null', () => {
    expect(parseQuickGrade('математика', S, 0)).toBeNull();
    expect(parseQuickGrade('13 математика', S, 0)).toBeNull();
    expect(parseQuickGrade('10 ма', S, 0)).toBeNull();
  });
});

describe('parseQuickGrade: предмети з крапками', () => {
  it('«англ» і «укр» знаходять мови, «мова» — неоднозначно', () => {
    const S = SUBJECT_LIST as readonly string[];
    expect(parseQuickGrade('9 англ', S, 0)?.subject).toBe('Англ. мова');
    expect(parseQuickGrade('9 укр', S, 0)?.subject).toBe('Укр. мова');
    expect(parseQuickGrade('9 мова', S, 0)).toBeNull();
  });
});
