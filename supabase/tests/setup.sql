-- Підготовка ЧИСТОЇ тимчасової бази перед знімком схеми (лише для тестів у CI, НЕ для продакшну).
--
-- `supabase db start` піднімає голий Postgres Supabase: є ролі anon/authenticated, схема auth
-- з auth.uid() і розширення, але немає таблиць сервісу Realtime. Знімок схеми створює політики
-- на realtime.messages — тому, якщо їх немає, додаємо мінімальні заглушки з тими самими іменами.

create schema if not exists realtime;

do $$
begin
  if to_regclass('realtime.messages') is null then
    create table realtime.messages (
      topic text not null,
      extension text not null,
      payload jsonb,
      inserted_at timestamptz not null default now()
    );
    alter table realtime.messages enable row level security;
  end if;

  if to_regprocedure('realtime.topic()') is null then
    -- У справжньому Realtime тема береться з налаштування сесії, яке ставить сервіс.
    create function realtime.topic() returns text
      language sql stable
      as $f$ select nullif(current_setting('realtime.topic', true), '') $f$;
  end if;
end $$;

create extension if not exists pgcrypto with schema extensions;
create extension if not exists pgtap with schema extensions;
