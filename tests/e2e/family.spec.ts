import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

const REF = 'vkwkyhjjjmcpmiakxohw';
const today = '2026-09-22';

async function mockFamily(page: Page, role: 'parent' | 'child', noKids = false) {
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
    if (p.endsWith('/school_members')) return r.fulfill(json(sel.startsWith('family_id') ? [{ family_id: 'f1', role }] : members));
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

test('офлайн-банер і тост помилки мережі', async ({ page, context }) => {
  await mockFamily(page, 'child');
  await page.goto('/#grades');
  await expect(page.locator('.sum')).toBeVisible();
  await page.route('**/rest/v1/school_grades*', (r) => (r.request().method() === 'POST' ? r.abort('internetdisconnected') : r.fallback()));
  await context.setOffline(true);
  await expect(page.locator('.offline')).toBeVisible();
  await page.getByRole('button', { name: /Оцінка 12/ }).click();
  await expect(page.getByRole('alert').locator('.toast')).toContainText('Немає інтернету');
  await context.setOffline(false);
  await expect(page.locator('.offline')).toHaveCount(0);
});
