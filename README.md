# Розклад уроків

Шкільний розклад з таймером уроків, домашкою, оцінками, нагородами та push-сповіщеннями для сім'ї.

**Стек:** Vite · TypeScript (strict) · lit-html · Supabase (Auth, Postgres + RLS, Realtime, Edge Functions) · PWA.

## Розробка
```bash
npm ci
npm run dev        # http://localhost:5173
npm run check      # typecheck + eslint + unit-тести
npm run build && npm run e2e   # Playwright: мобільний + десктоп, axe WCAG 2.2 AA
```

## Структура
- `app/index.html` — HTML-оболонка (Vite root)
- `src/data` — розклад і предмети
- `src/lib` — час, емодзі (чисті функції)
- `src/features/rewards.ts` — доменна логіка нагород (покрита тестами)
- `src/views` — екрани (lit-html, автоекранування → без XSS)
- `src/ui` — діалоги (`<dialog>`), поповери, конфеті, байк (CSS-анімація)
- `supabase/functions/school-push` — web push

## Безпека
CSP у production-збірці, усі дані захищені RLS, дитина не може підтверджувати оцінки, змінювати курс чи нараховувати бонуси.

Іконки: [Microsoft Fluent Emoji 3D](https://github.com/microsoft/fluentui-emoji) (MIT).
