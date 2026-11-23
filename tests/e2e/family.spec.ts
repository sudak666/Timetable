import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

const REF = 'vkwkyhjjjmcpmiakxohw';
const today = '2026-09-22';

/** Розклад Стаса (як приклад за замовчуванням): у вівторок є Інформатика — під неї домашка h1. */
const TIMETABLE = {
  child_id: 'k1',
  bells: [['8:30', '9:15'], ['9:25', '10:10'], ['10:30', '11:15'], ['11:25', '12:10'], ['12:30', '13:15'], ['13:35', '14:20'], ['14:30', '15:15']],
  week: [
    ['Математика', 'Всесв. історія', 'Література', 'Англ. мова', 'Укр. мова', 'STEM'],
    ['Література', 'Інформатика', 'Укр. мова', 'Географія', 'Математика', 'Фіз-ра'],
    ['Англ. мова', 'Математика', 'Технології', 'Укр. мова', 'Громад. освіта', 'Довкілля', 'STEM'],
    ['Математика', 'Укр. мова', 'Географія', 'Фіз-ра', 'Англ. мова', 'Мистецтво', 'ЗБД'],
    ['Всесв. історія', 'Англ. мова', 'Література', 'Фіз-ра', 'Довкілля', 'Математика'],
  ].map((d) => d.map((x) => ({ s: x }))),
  holidays: [],
  // 2026-09-22 — вівторок: футбол сьогодні, програмування в середу.
  activities: [
    { t: 'Футбол', e: '⚽', d: [2, 4], a: '16:00', b: '17:30', p: 'Стадіон', n: 'форма, бутси' },
    { t: 'Програмування', e: '💻', d: [3], a: '18:00', b: '19:00', p: null, n: null },
  ],
};

async function mockFamily(page: Page, role: 'parent' | 'child', noKids = false, { timetable = true, family = true } = {}) {
  const meId = role === 'parent' ? 'p1' : 'k1';
  const user = { id: meId, aud: 'authenticated', role: 'authenticated', email: `${meId}@test.ua`, user_metadata: {}, app_metadata: {} };
  const exp = Math.floor(new Date('2026-09-23').getTime() / 1000);
  await page.addInitScript(([ref, u, e]) => {
    localStorage.setItem(`sb-${ref}-auth-token`, JSON.stringify({ access_token: 'x.eyJzdWIiOiJ4In0.x', refresh_token: 'r', token_type: 'bearer', expires_in: 3600, expires_at: e, user: u }));
  }, [REF, user, exp] as const);
  const members = [
    { user_id: 'p1', role: 'parent', name: 'Тато', goal_title: '', goal_amount: 0, avatar: '', theme: '' },
    { user_id: 'k1', role: 'child', name: 'Стас', goal_title: 'Електробайк', goal_amount: 2000, avatar: '🦊', theme: '' },
  ];
  if (noKids) members.pop();
  const grades = [
    { id: 'g1', child_id: 'k1', subject: 'Математика', grade: 12, date: today, status: 'approved', amount: 150, created_at: today + 'T09:00' },
    { id: 'g2', child_id: 'k1', subject: 'Англ. мова', grade: 4, date: today, status: 'pending', amount: null, created_at: today + 'T10:00' },
  ];
  const json = (b: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(b) });
  await page.route(`https://${REF}.supabase.co/**`, (r) => {
    const u = new URL(r.request().url());
    const p = u.pathname, sel = u.searchParams.get('select') ?? '';
    if (p.endsWith('/auth/v1/user')) return r.fulfill(json(user));
    if (p.endsWith('/school_members')) return r.fulfill(json(sel.startsWith('family_id') ? (family ? [{ family_id: 'f1', role }] : []) : members));
    if (p.endsWith('/school_timetables')) return r.fulfill(json(timetable && !noKids ? [TIMETABLE] : []));
    if (p.endsWith('/school_families')) return r.fulfill(json({ id: 'f1', name: 'Сімʼя Тест', invite_code: 'ABC123', rates: {} }));
    if (p.endsWith('/school_grades')) return r.fulfill(json(grades));
    if (p.endsWith('/school_ledger')) return r.fulfill(json([{ id: 'l1', child_id: 'k1', kind: 'bonus', amount: 50, note: 'Молодець', date: today, created_at: today + 'T11:00' }]));
    if (p.endsWith('/school_challenges')) return r.fulfill(json([{ id: 'c1', child_id: 'k1', title: '2 дванадцятки', subject: null, min_grade: 12, need: 2, reward: 100, start_date: today, end_date: '2026-09-28' }]));
    if (p.endsWith('/school_homework')) return r.fulfill(json([{ id: 'h1', child_id: 'k1', subject: 'Інформатика', due: today, text: 'Презентація', done: false }]));
    if (p.includes('/rpc/school_parent_code')) return r.fulfill(json('PARENT01'));
    return r.fulfill(json([]));
  });
  await page.clock.install({ time: new Date(today + 'T09:40:00') });
}

for (const role of ['child', 'parent'] as const) {
  test(`головний екран: ${role}`, async ({ page }) => {
    await mockFamily(page, role);
    await page.goto('/');
    await expect(page.locator('.prof')).toBeVisible();
    await expect(page.locator('.day.today')).toContainText('ДЗ');
    // Позашкільні заняття: картка «Після школи» і речі в рюкзаку.
    await expect(page.locator('.after')).toContainText('Футбол');
    await expect(page.locator('.after')).toContainText('16:00 – 17:30');
    await expect(page.locator('.club-bag')).toContainText('форма, бутси');
    await expect(page.locator('.bnav .nb')).toHaveCount(2);
    await page.goto('/#grades');
    await expect(page.locator('.sum')).toContainText('+200 ₴');
    await expect(page.locator('.gi.wait')).toContainText('чекає підтвердження');
    if (role === 'parent') {
      await expect(page.getByRole('button', { name: 'Підтвердити', exact: true })).toBeVisible();
      await expect(page.getByRole('tab', { name: /Стас/ })).toHaveAttribute('aria-selected', 'true');
    } else {
      await expect(page.getByRole('button', { name: 'Підтвердити', exact: true })).toHaveCount(0);
    }
    for (const tab of ['today', 'grades', 'hw', 'me'])
    for (const scheme of ['light', 'dark'] as const) {
      await page.goto('/#' + tab);
      await expect(page.locator('main')).not.toContainText('Завантаження');
      await page.emulateMedia({ colorScheme: scheme, reducedMotion: 'reduce' });
      const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
      expect(r.violations.map((v) => `${tab} ${scheme} ${v.id}: ${v.nodes.map((n) => n.target.join(' ') + ' ' + (n.any[0]?.message ?? '')).join(' | ')}`)).toEqual([]);
    }
  });
}

test('батьки без дітей бачать картку запрошення з кодом', async ({ page }) => {
  await mockFamily(page, 'parent', true);
  await page.goto('/#grades');
  await expect(page.getByRole('heading', { name: 'Запросіть дитину' })).toBeVisible();
  await expect(page.locator('.invite .code')).toHaveText('ABC123');
});

test('онбординг дитини показується один раз', async ({ page }) => {
  await mockFamily(page, 'child');
  await page.goto('/#grades');
  const intro = page.getByRole('heading', { name: /Як це працює/ });
  await expect(intro).toBeVisible();
  await page.getByRole('button', { name: /поїхали/ }).click();
  await expect(intro).toHaveCount(0);
  await page.reload();
  await expect(page.locator('.sum')).toBeVisible();
  await expect(intro).toHaveCount(0);
});

test('офлайн: оцінка стає в чергу, видна одразу й відправляється після підключення', async ({ page, context }) => {
  await mockFamily(page, 'child');
  await page.goto('/#grades');
  await expect(page.locator('.sum')).toBeVisible();
  const posts: unknown[] = [];
  await page.route('**/rest/v1/school_grades*', (r) => {
    if (r.request().method() !== 'POST') return r.fallback();
    posts.push(r.request().postDataJSON());
    return r.fulfill({ status: 201, body: '' });
  });
  await context.setOffline(true);
  await expect(page.locator('.offline')).toBeVisible();
  await page.getByRole('button', { name: /Оцінка 11/ }).click();
  await expect(page.locator('.offline')).toContainText('1');
  expect(posts).toHaveLength(0);
  await context.setOffline(false);
  await expect(page.locator('.offline')).toHaveCount(0);
  expect(posts).toHaveLength(1);
  expect(posts[0]).toMatchObject({ child_id: 'k1', grade: 11, status: 'pending', amount: null });
  expect(await page.evaluate(() => localStorage.getItem('outbox:k1'))).toBeNull();
});

test('без мережі при старті показує збережені дані', async ({ page }) => {
  await mockFamily(page, 'child');
  await page.goto('/#hw');
  await expect(page.getByText('Презентація')).toBeVisible();
  await page.route(`https://${REF}.supabase.co/**`, (r) => r.abort('internetdisconnected'));
  await page.reload();
  await expect(page.getByText('Презентація')).toBeVisible();
});

test('Command Palette: Ctrl+K, навігація й швидка оцінка', async ({ page }) => {
  await mockFamily(page, 'parent');
  await page.goto('/#grades');
  await expect(page.locator('.sum')).toBeVisible();
  const post = page.waitForRequest((r) => r.method() === 'POST' && r.url().includes('school_grades'));
  await page.keyboard.press('Control+k');
  const box = page.getByRole('combobox');
  await expect(box).toBeFocused();
  await box.fill('11 матем вчора');
  await expect(page.getByRole('option').first()).toContainText('11 · Математика');
  const a11y = await new AxeBuilder({ page }).include('dialog').withTags(['wcag2a', 'wcag2aa', 'wcag22aa']).analyze();
  expect(a11y.violations.map((v) => v.id)).toEqual([]);
  await page.keyboard.press('Enter');
  expect((await post).postDataJSON()).toMatchObject({ grade: 11, subject: 'Математика', child_id: 'k1', status: 'approved', date: '2026-09-21' });
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.keyboard.press('3');
  await expect(page).toHaveURL(/#hw\?kid=k1$/);
});

test('увійшов без розкладу: замість чужого прикладу — «Заповнити розклад»', async ({ page }) => {
  await mockFamily(page, 'child', false, { timetable: false });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Розклад ще не заповнено' })).toBeVisible();
  await expect(page.locator('.day')).toHaveCount(0);
  await expect(page.locator('.now')).toHaveCount(0);
  await page.getByRole('button', { name: /Заповнити розклад/ }).click();
  await expect(page.getByRole('heading', { name: /Розклад · Стас/ })).toBeVisible();
});

test('увійшов, але ще не в сім’ї: підказка веде в «Профіль»', async ({ page }) => {
  await mockFamily(page, 'parent', false, { family: false });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Розклад ще не заповнено' })).toBeVisible();
  await expect(page.locator('.day')).toHaveCount(0);
  await page.getByRole('button', { name: 'Перейти в «Профіль»' }).click();
  await expect(page).toHaveURL(/#me$/);
});

test('профіль: батьки змінюють своє ім’я й назву сім’ї', async ({ page }) => {
  await mockFamily(page, 'parent');
  await page.goto('/#me');
  const patch = (table: string) => page.waitForRequest((r) => r.method() === 'PATCH' && r.url().includes(table));
  await page.getByRole('button', { name: /Ім’я/ }).click();
  await page.getByRole('dialog').locator('input').fill('  Сергій Петрович  ');
  let req = patch('school_members');
  await page.getByRole('button', { name: 'Готово' }).click();
  expect((await req).postDataJSON()).toEqual({ name: 'Сергій Петрович' });
  expect((await req).url()).toContain('user_id=eq.p1');

  await page.getByRole('button', { name: /Назва сім’ї/ }).click();
  await page.getByRole('dialog').locator('input').fill('Сім’я Коваленків');
  req = patch('school_families');
  await page.getByRole('button', { name: 'Готово' }).click();
  expect((await req).postDataJSON()).toEqual({ name: 'Сім’я Коваленків' });
});

test('профіль: дитина не бачить зміну назви сім’ї', async ({ page }) => {
  await mockFamily(page, 'child');
  await page.goto('/#me');
  await expect(page.getByRole('button', { name: /Ім’я/ })).toBeVisible();
  await expect(page.getByRole('button', { name: /Назва сім’ї/ })).toHaveCount(0);
});

test('вступ у сім’ю без імені не пускає', async ({ page }) => {
  await mockFamily(page, 'child', false, { family: false });
  let rpc = 0;
  await page.route('**/rest/v1/rpc/school_join_family', (r) => { rpc++; return r.fulfill({ status: 204, body: '' }); });
  await page.goto('/#me');
  await page.getByPlaceholder('Код запрошення').fill('ABC123');
  await page.getByRole('button', { name: /Приєднатися/ }).click();
  await expect(page.getByRole('status')).toContainText('Напиши своє ім’я');
  await expect(page.getByPlaceholder("Твоє ім'я")).toBeFocused();
  expect(rpc).toBe(0);
});

test('«Забули пароль?» надсилає лист на введену пошту', async ({ page }) => {
  let body: unknown = null, url = '';
  await page.route(`https://${REF}.supabase.co/auth/v1/recover**`, (r) => {
    body = r.request().postDataJSON(); url = r.request().url();
    return r.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });
  await page.goto('/#me');
  await page.locator('#gEmail').fill('kid@test.ua');
  await page.getByRole('button', { name: 'Забули пароль?' }).click();
  await expect(page.getByRole('dialog')).toContainText('kid@test.ua');
  expect(body).toMatchObject({ email: 'kid@test.ua' });
  expect(decodeURIComponent(url)).toContain('redirect_to=http://localhost');
});

test('«Змінити пароль» у профілі: двічі, перевірка збігу, збереження', async ({ page }) => {
  await mockFamily(page, 'parent');
  const puts: unknown[] = [];
  await page.route(`https://${REF}.supabase.co/auth/v1/user`, (r) => {
    if (r.request().method() === 'PUT') puts.push(r.request().postDataJSON());
    return r.fallback();
  });
  // Have I Been Pwned: «password1» — злитий (SHA-1 E38AD…), решта — ні. У мережу йде лише префікс із 5 символів.
  const ranges: string[] = [];
  await page.route('https://api.pwnedpasswords.com/range/*', (r) => {
    const prefix = r.request().url().split('/').pop()!;
    ranges.push(prefix);
    return r.fulfill({ body: prefix === 'E38AD' ? '214943DAAD1D64C102FAEC29DE4AFE9DA3D:2400000\r\n' : '0000000000000000000000000000000000A:0\r\n' });
  });
  await page.goto('/#me');
  const change = page.getByRole('button', { name: /Змінити пароль/ });
  const enter = async (v: string) => { await page.getByRole('dialog').locator('input[type=password]').fill(v); await page.getByRole('button', { name: 'Готово' }).click(); };
  await change.click(); await enter('short');
  await expect(page.locator('.toast', { hasText: 'від 8 символів' })).toBeVisible();
  await change.click(); await enter('password1');
  await expect(page.locator('.toast', { hasText: 'витікав' })).toBeVisible();
  expect(ranges).toContain('E38AD');
  expect(ranges.every((x) => /^[0-9A-F]{5}$/.test(x))).toBe(true);
  await change.click(); await enter('newpass1'); await enter('newpass2');
  await expect(page.locator('.toast', { hasText: 'не збігаються' })).toBeVisible();
  expect(puts).toHaveLength(0);
  await change.click(); await enter('newpass1'); await enter('newpass1');
  await expect.poll(() => puts.length).toBe(1);
  expect(puts[0]).toMatchObject({ password: 'newpass1' });
});

test('посилання з листа відкриває «Задай новий пароль»', async ({ page }) => {
  await mockFamily(page, 'parent');
  await page.addInitScript(() => localStorage.clear());
  await page.goto('/#access_token=x.eyJzdWIiOiJ4In0.x&expires_at=1790000000&expires_in=3600&refresh_token=r&token_type=bearer&type=recovery');
  await expect(page.getByRole('dialog')).toContainText('Задай новий пароль');
  await expect(page).not.toHaveURL(/access_token/);
});

test('редактор розкладу: заповнити, перевірка помилок, збереження на сервер', async ({ page }) => {
  await mockFamily(page, 'parent', false, { timetable: false });
  await page.goto('/');
  await page.getByRole('button', { name: /Заповнити розклад/ }).click();
  const heading = page.getByRole('heading', { name: /Розклад · Стас/ });
  await expect(heading).toBeVisible();
  const save = page.getByRole('button', { name: 'Зберегти' });
  await expect(save).toBeDisabled();

  // Понеділок: свій предмет, потім предмет зі списку, факультатив і перестановка.
  await page.getByRole('button', { name: '+ Свій предмет' }).click();
  await page.getByRole('dialog').locator('input').fill('Робототехніка');
  await page.getByRole('button', { name: 'Готово' }).click();
  await page.getByRole('button', { name: '+ Урок' }).click();
  await page.getByRole('option', { name: /Математика/ }).first().click();
  await page.locator('.ed-list .opt input').nth(1).check();
  await page.getByRole('button', { name: 'Перемістити 2 урок вище' }).click();

  // Заняття: шаблон, день, час і що взяти; без дня — помилка.
  await page.getByRole('tab', { name: 'Заняття' }).click();
  await page.getByRole('button', { name: /Шахи/ }).click();
  await expect(page.locator('.ed-err')).toContainText('Шахи: обери хоча б один день');
  await page.getByRole('group', { name: 'Заняття 1: дні' }).getByRole('button', { name: 'Ср' }).click();
  await page.getByLabel('Заняття 1: початок').fill('15:00');
  await page.getByLabel('Заняття 1: кінець').fill('16:00');
  await page.getByLabel('Заняття 1: що взяти').fill('блокнот');

  // Дзвінки: помилка блокує збереження, виправлення — знімає.
  await page.getByRole('tab', { name: 'Дзвінки' }).click();
  await page.getByLabel('1 урок: початок').fill('08:00');
  await page.getByLabel('1 урок: кінець').fill('07:50');
  await expect(page.locator('.ed-err')).toContainText('1 урок: кінець має бути пізніше за початок');
  await expect(save).toBeDisabled();
  await page.getByLabel('1 урок: кінець').fill('08:45');
  await expect(page.locator('.ed-err')).toHaveCount(0);

  for (const scheme of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme: scheme, reducedMotion: 'reduce' });
    const r = await new AxeBuilder({ page }).include('.editor').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
    expect(r.violations.map((v) => `${scheme} ${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
  }

  const post = page.waitForRequest((r) => r.method() === 'POST' && r.url().includes('school_timetables'));
  await save.click();
  const body = (await post).postDataJSON();
  expect(body).toMatchObject({
    child_id: 'k1',
    family_id: 'f1',
    week: [[{ s: 'Математика', o: true }, { s: 'Робототехніка' }], [], [], [], []],
    holidays: [],
    activities: [{ t: 'Шахи', e: '♟️', d: [3], a: '15:00', b: '16:00', p: null, n: 'блокнот' }],
  });
  expect(body.bells[0]).toEqual(['8:00', '8:45']);
  expect(body.bells).toHaveLength(7);
  await expect(heading).toHaveCount(0);
});

test('редактор розкладу: незбережені зміни не губляться без підтвердження', async ({ page }) => {
  await mockFamily(page, 'child', false, { timetable: false });
  await page.goto('/');
  await page.getByRole('button', { name: /Заповнити розклад/ }).click();
  await page.getByRole('button', { name: '+ Свій предмет' }).click();
  await page.getByRole('dialog').locator('input').fill('Шахи');
  await page.getByRole('button', { name: 'Готово' }).click();
  await page.getByRole('button', { name: 'Скасувати' }).click();
  await expect(page.getByRole('dialog')).toContainText('Скинути незбережені зміни');
  await page.getByRole('dialog').getByRole('button', { name: 'Скасувати' }).click();
  await expect(page.getByRole('button', { name: /Шахи/ })).toBeVisible();
});

test('редактор розкладу: чужа зміна під час редагування — питає, чи записати поверх', async ({ page }) => {
  await mockFamily(page, 'parent');
  const v1 = '2026-09-21T10:00:00.123456+00:00';
  const writes: { method: string; url: string }[] = [];
  await page.route(`https://${REF}.supabase.co/rest/v1/school_timetables**`, (r) => {
    const m = r.request().method();
    if (m === 'GET') return r.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([{ ...TIMETABLE, updated_at: v1 }]) });
    writes.push({ method: m, url: r.request().url() });
    // PATCH з умовою updated_at=v1 не зачепив рядка: його вже змінили деінде.
    return r.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });
  await page.goto('/');
  await page.getByRole('button', { name: /Редагувати розклад/ }).click();
  await page.getByRole('button', { name: 'Прибрати 1 урок' }).click();
  await page.getByRole('button', { name: 'Зберегти' }).click();
  await expect(page.getByRole('dialog')).toContainText('Розклад уже змінили на іншому пристрої');
  expect(writes[0]?.method).toBe('PATCH');
  expect(decodeURIComponent(writes[0]!.url)).toContain(`updated_at=eq.${v1}`);
  await page.getByRole('dialog').getByRole('button', { name: 'Записати мою' }).click();
  await expect.poll(() => writes.length).toBe(2);
  expect(writes[1]?.method).toBe('POST');
  await expect(page.getByRole('heading', { name: /Розклад · Стас/ })).toHaveCount(0);
});

test('домашку від батьків дитина не видаляє — кнопки «✕» немає', async ({ page }) => {
  await mockFamily(page, 'child');
  await page.route(`https://${REF}.supabase.co/rest/v1/school_homework**`, (r) => r.fulfill({
    status: 200, contentType: 'application/json',
    body: JSON.stringify([
      { id: 'h1', child_id: 'k1', subject: 'Інформатика', due: today, text: 'Від тата', done: false, created_by: 'p1' },
      { id: 'h2', child_id: 'k1', subject: 'Математика', due: today, text: 'Своя', done: false, created_by: 'k1' },
    ]),
  }));
  await page.goto('/#hw');
  await expect(page.locator('#hwl li')).toHaveCount(2);
  await expect(page.locator('#hwl li', { hasText: 'Від тата' }).getByRole('button', { name: 'Видалити завдання' })).toHaveCount(0);
  await expect(page.locator('#hwl li', { hasText: 'Своя' }).getByRole('button', { name: 'Видалити завдання' })).toHaveCount(1);
});

/* ---------------- повітряні тривоги ---------------- */

const KYIV = { uid: 31, name: 'м. Київ', kind: 'city', oblast_uid: null, raion_uid: null };
const KYIV_OBL = { uid: 14, name: 'Київська область', kind: 'oblast', oblast_uid: null, raion_uid: null };
const IN_OBL = [
  { uid: 100, name: 'Бучанський район', kind: 'raion', oblast_uid: 14, raion_uid: null },
  { uid: 111, name: 'Боярська територіальна громада', kind: 'hromada', oblast_uid: 14, raion_uid: 100 },
  { uid: 112, name: 'Бучанська територіальна громада', kind: 'hromada', oblast_uid: 14, raion_uid: 100 },
];

/** Тривоги поверх mockFamily: місце сім'ї, довідник і стан; PATCH school_families змінює місце. */
async function mockAlerts(page: Page, start: { uid: number | null; status: 'A' | 'P' | 'N' | null }) {
  const air = { ...start }, patches: unknown[] = [];
  const json = (b: unknown) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(b) });
  await page.route(`https://${REF}.supabase.co/rest/v1/school_*`, async (r) => {
    const u = new URL(r.request().url()), p = u.pathname, q = u.searchParams;
    if (p.endsWith('/school_families')) {
      if (r.request().method() === 'PATCH') {
        const body = r.request().postDataJSON() as { alert_uid: number | null };
        patches.push(body);
        air.uid = body.alert_uid;
        air.status = null;
        return r.fulfill(json([{ id: 'f1' }]));
      }
      return r.fulfill(json({ id: 'f1', name: 'Сімʼя Тест', invite_code: 'ABC123', rates: {}, alert_uid: air.uid }));
    }
    if (p.endsWith('/school_alert_locations')) {
      const all = [KYIV, KYIV_OBL, ...IN_OBL];
      if (q.get('kind')) return r.fulfill(json([KYIV_OBL, KYIV]));
      if (q.get('oblast_uid') === 'eq.14') return r.fulfill(json(IN_OBL));
      const uid = Number(q.get('uid')?.slice(3));
      return r.fulfill(json(all.filter((l) => l.uid === uid)));
    }
    if (p.endsWith('/school_alert_state')) {
      return r.fulfill(json(air.status && Number(q.get('uid')?.slice(3)) === air.uid
        ? [{ status: air.status, since: today + 'T09:05:00', checked_at: today + 'T09:39:00' }] : []));
    }
    return r.fallback();
  });
  return { air, patches };
}

test('тривоги: плашка, батьки обирають громаду пошуком і прибирають місце', async ({ page }) => {
  await mockFamily(page, 'parent');
  const m = await mockAlerts(page, { uid: 31, status: 'A' });
  await page.goto('/');
  const bar = page.locator('.air');
  await expect(bar).toHaveClass(/on/);
  await expect(bar.getByRole('status')).toHaveText(/Повітряна тривога · м\. Київ/);
  await expect(bar).toContainText('з 9:05 · станом на 9:39. Пройдіть в укриття.');

  await page.goto('/#me');
  const card = page.getByRole('region', { name: 'Повітряні тривоги' });
  await expect(card).toContainText('м. Київ');
  await card.getByRole('button', { name: /Змінити місце/ }).click();
  const dlg = page.getByRole('dialog', { name: 'Місце для тривог' });
  await expect(dlg.getByRole('button', { name: /^м\. Київ Тривоги оголошують окремо/ })).toBeVisible();
  // Окреме місто — першим, далі області.
  await expect(dlg.locator('.rg-o').first()).toContainText('м. Київ');
  for (const scheme of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme: scheme, reducedMotion: 'reduce' });
    const r = await new AxeBuilder({ page }).include('dialog').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
    expect(r.violations.map((v) => `${scheme} ${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
  }
  await dlg.getByRole('button', { name: 'Київська область', exact: true }).click();
  const area = page.getByRole('dialog', { name: 'Київська область' });
  await expect(area.getByRole('button', { name: /Уся область/ })).toBeVisible();
  {
    const r = await new AxeBuilder({ page }).include('dialog').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
    expect(r.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
  }
  await area.getByRole('searchbox', { name: 'Пошук громади чи району' }).fill('боярськ');
  await expect(area.locator('.rg-o.hrom')).toHaveCount(1);
  await expect(area.getByRole('button', { name: /^Бучанський район Увесь район/ })).toBeVisible();
  await area.getByRole('button', { name: 'Боярська громада', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(m.patches).toEqual([{ alert_uid: 111 }]);
  await expect(card).toContainText('Боярська територіальна громада');
  await expect(page.locator('.air')).toHaveCount(0);

  await card.getByRole('button', { name: 'Прибрати місце' }).click();
  await page.getByRole('button', { name: 'Прибрати', exact: true }).click();
  await expect(card).toContainText('Місце не обрано');
  expect(m.patches).toEqual([{ alert_uid: 111 }, { alert_uid: null }]);
});

test('тривоги: дитина бачить «частково» й місце, але не змінює', async ({ page }) => {
  await mockFamily(page, 'child');
  await mockAlerts(page, { uid: 31, status: 'P' });
  await page.goto('/#me');
  const bar = page.locator('.air');
  await expect(bar).not.toHaveClass(/on/);
  await expect(bar).toContainText('Тривога в частині: м. Київ');
  await expect(bar).not.toContainText('укриття');
  const card = page.getByRole('region', { name: 'Повітряні тривоги' });
  await expect(card).toContainText('м. Київ');
  await expect(card.getByRole('button')).toHaveCount(0);
});

test('тривоги: стан оновлюється щохвилини без перезавантаження', async ({ page }) => {
  await mockFamily(page, 'parent');
  const m = await mockAlerts(page, { uid: 31, status: 'A' });
  await page.goto('/');
  await expect(page.locator('.air.on')).toBeVisible();
  m.air.status = 'N';
  await page.clock.runFor(61_000);
  await expect(page.locator('.air')).toHaveCount(0);
});
