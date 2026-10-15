# Розклад уроків

Шкільний розклад з таймером уроків, домашкою, оцінками, нагородами та push-сповіщеннями для сім'ї.

**Стек:** Vite · TypeScript (strict) · lit-html · Supabase (модульно: auth-js + postgrest-js, realtime-js — lazy; Postgres + RLS, Edge Functions) · PWA.

## Розробка
```bash
npm ci
npm run dev        # http://localhost:5173
npm run check      # typecheck + eslint + unit-тести
npm run build && npm run size  # бюджет: JS ≤ 40 КБ, CSS ≤ 12 КБ gzip
npm run e2e        # Playwright: мобільний + десктоп, axe WCAG 2.2 AA
```

## Структура
- `app/index.html` — HTML-оболонка (Vite root)
- `src/data` — розклад і предмети
- `src/lib` — час, емодзі (чисті функції)
- `src/features/rewards.ts` — доменна логіка нагород (покрита тестами)
- `src/views` — екрани (lit-html, автоекранування → без XSS)
- `src/ui` — діалоги (`<dialog>`), поповери, конфеті, байк (CSS-анімація)
- `supabase/functions/school-push` — web push

## Клавіатура
`Ctrl/⌘+K` або `/` — команди (перехід, день розкладу, дитина, тема; «10 матем вчора» — швидка оцінка) · `1–4` — вкладки · `T` — тема.

## Офлайн
Останні дані зберігаються на пристрої й показуються без мережі. Нові оцінки та домашка (і позначка «виконано») стають у чергу з клієнтським UUID і відправляються після підключення — повтор ідемпотентний.

## Сповіщення
Події оцінок/бонусів/челенджів — тригери → `school-push`. Нагадування про домашку на завтра — `pg_cron` (нд–чт 16:00 UTC), міграція в `supabase/migrations`.

## Безпека
CSP у production-збірці, усі дані захищені RLS, дитина не може підтверджувати оцінки, змінювати курс чи нараховувати бонуси.

Іконки: [Microsoft Fluent Emoji 3D](https://github.com/microsoft/fluentui-emoji) (MIT).
