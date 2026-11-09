import { describe, expect, it } from 'vitest';
import { alertText, effectiveStatus, parseStatuses, transitions, type AlertLocation, type AlertState } from '../../supabase/functions/school-push/alerts.ts';

/** Рядок статусів довжиною 2000 з потрібними символами на позиціях UID. */
function statuses(set: Record<number, 'A' | 'P' | 'N'> = {}): string {
  const s = Array.from({ length: 2000 }, () => 'N');
  for (const [uid, c] of Object.entries(set)) s[Number(uid)] = c;
  return s.join('');
}

const kharkivOblast: AlertLocation = { uid: 22, name: 'Харківська область', kind: 'oblast', oblast_uid: null, raion_uid: null, separate: false };
const kharkivRaion: AlertLocation = { uid: 124, name: 'Харківський район', kind: 'raion', oblast_uid: 22, raion_uid: null, separate: false };
const kharkivCity: AlertLocation = { uid: 1293, name: 'м. Харків та Харківська територіальна громада', kind: 'hromada', oblast_uid: 22, raion_uid: 124, separate: true };
const hromada: AlertLocation = { uid: 1300, name: 'Дергачівська територіальна громада', kind: 'hromada', oblast_uid: 22, raion_uid: 124, separate: false };
const kyiv: AlertLocation = { uid: 31, name: 'м. Київ', kind: 'city', oblast_uid: null, raion_uid: null, separate: true };

describe('відповідь API', () => {
  it('приймає рядок, що покриває потрібні UID', () => {
    expect(parseStatuses(statuses(), 1300)).toHaveLength(2000);
  });
  it('відкидає не-рядок, обрізаний рядок і чужі символи — краще пропустити хвилину, ніж хибний «відбій»', () => {
    expect(parseStatuses({ message: 'Error' }, 31)).toBeNull();
    expect(parseStatuses('N'.repeat(100), 1300)).toBeNull();
    expect(parseStatuses('N'.repeat(30), 5)).toBeNull();
    expect(parseStatuses(statuses().replace(/^N/, 'X'), 31)).toBeNull();
  });
  it('пробіл — невикористаний UID — дозволений', () => {
    expect(parseStatuses(' '.repeat(3) + 'N'.repeat(1997), 31)).not.toBeNull();
  });
});

describe('стан місця з урахуванням ієрархії', () => {
  it('тривога в області діє на район і громаду', () => {
    const s = statuses({ 22: 'A' });
    expect(effectiveStatus(kharkivRaion, s)).toBe('A');
    expect(effectiveStatus(hromada, s)).toBe('A');
  });
  it('тривога в районі діє на його громаду, але не на область', () => {
    const s = statuses({ 124: 'A', 22: 'P' });
    expect(effectiveStatus(hromada, s)).toBe('A');
    expect(effectiveStatus(kharkivOblast, s)).toBe('P');
  });
  it('окреме місто — лише за власним статусом', () => {
    expect(effectiveStatus(kharkivCity, statuses({ 22: 'A', 124: 'A' }))).toBe('N');
    expect(effectiveStatus(kharkivCity, statuses({ 1293: 'A' }))).toBe('A');
    expect(effectiveStatus(kyiv, statuses({ 14: 'A' }))).toBe('N');
  });
  it('для громади «частково» не буває', () => {
    expect(effectiveStatus(hromada, statuses({ 1300: 'P', 124: 'P' }))).toBe('N');
  });
  it('район у частковій тривозі лишається «P»', () => {
    expect(effectiveStatus(kharkivRaion, statuses({ 124: 'P' }))).toBe('P');
  });
});

describe('переходи й події', () => {
  const t0 = new Date('2026-11-10T08:00:00Z');
  const t1 = new Date('2026-11-10T08:01:00Z');
  const prev = (status: 'A' | 'P' | 'N'): AlertState[] => [{ uid: 31, status, since: t0.toISOString(), checked_at: t0.toISOString() }];

  it('перший запис (місце щойно обрали) — без сповіщення', () => {
    const r = transitions([kyiv], statuses({ 31: 'A' }), [], t1);
    expect(r.events).toEqual([]);
    expect(r.states).toEqual([{ uid: 31, status: 'A', since: t1.toISOString(), checked_at: t1.toISOString() }]);
  });
  it('початок і відбій — події; since оновлюється лише при зміні', () => {
    const start = transitions([kyiv], statuses({ 31: 'A' }), prev('N'), t1);
    expect(start.events).toEqual([{ uid: 31, name: 'м. Київ', kind: 'start', since: t1.toISOString() }]);
    const same = transitions([kyiv], statuses({ 31: 'A' }), prev('A'), t1);
    expect(same.events).toEqual([]);
    expect(same.states[0]).toMatchObject({ since: t0.toISOString(), checked_at: t1.toISOString() });
    const end = transitions([kyiv], statuses(), prev('A'), t1);
    expect(end.events.map((e) => e.kind)).toEqual(['end']);
  });
  it('P↔N — тиха зміна без push', () => {
    const r = transitions([kharkivOblast], statuses({ 22: 'P' }), [{ ...prev('N')[0]!, uid: 22 }], t1);
    expect(r.events).toEqual([]);
    expect(r.states[0]?.status).toBe('P');
  });
  it('A→P — це відбій для обраного місця', () => {
    const r = transitions([kharkivOblast], statuses({ 22: 'P' }), [{ ...prev('A')[0]!, uid: 22 }], t1);
    expect(r.events.map((e) => e.kind)).toEqual(['end']);
  });
});

describe('тексти', () => {
  it('тривога й відбій', () => {
    expect(alertText({ uid: 31, name: 'м. Київ', kind: 'start', since: '' })).toEqual({ title: '🚨 Повітряна тривога', body: 'м. Київ. Пройдіть в укриття.' });
    expect(alertText({ uid: 31, name: 'м. Київ', kind: 'end', since: '' }).title).toBe('✅ Відбій тривоги');
  });
});
