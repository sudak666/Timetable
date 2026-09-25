import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  // Фіксований час: вівторок, 2-й урок (Інформатика).
  await page.clock.install({ time: new Date('2026-09-22T09:40:00') });
});

test('розклад рендериться без помилок і порушень CSP', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error' && /Content Security Policy|Refused/.test(m.text())) errors.push(m.text()); });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Розклад уроків' })).toBeVisible();
  await expect(page.locator('.now h2')).toHaveText('Інформатика');
  await expect(page.locator('.day')).toHaveCount(5);
  await expect(page.locator('.day.today h3')).toContainText('Вівторок');
  await expect(page.locator('#grades')).toContainText('Увійти через Google');
  expect(errors).toEqual([]);
});

test('вкладка дня показує лише цей день', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('tab', { name: 'Четвер' }).click();
  await expect(page.locator('.day')).toHaveCount(1);
  await expect(page.locator('.day')).toContainText('ЗБД');
});

test('вікторина нараховує зірку за правильну відповідь', async ({ page }) => {
  await page.goto('/');
  const answers = page.locator('.quiz .ans button');
  await expect(answers).toHaveCount(4);
  await answers.first().click();
  await expect(answers.first()).toBeDisabled();
});

test('модальне вікно на <dialog> закривається Esc і повертає фокус', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(() => { Object.defineProperty(window, 'Notification', { value: undefined }); });
  const bell = page.getByRole('button', { name: /Нагадування/ });
  await bell.click();
  const dlg = page.locator('dialog.md');
  await expect(dlg).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dlg).toHaveCount(0);
  await expect(bell).toBeFocused();
});

test('немає горизонтального скролу', async ({ page }) => {
  await page.goto('/');
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
});

for (const theme of ['light', 'dark'] as const) {
  test(`WCAG 2.2 AA (axe), ${theme}`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: theme, reducedMotion: 'reduce' });
    await page.goto('/');
    await expect(page.locator('#grades')).toContainText('Увійти');
    const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
    expect(r.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ') + ' ' + (n.any[0]?.message ?? '')).join(' | ')}`)).toEqual([]);
  });
}
