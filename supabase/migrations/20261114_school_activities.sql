-- Позашкільні заняття дитини (тренування, гуртки, курси) — у тому ж рядку розкладу, що й уроки.
-- Ті самі RLS (батьки або сама дитина), той самий Realtime і та сама офлайн-черга в Android.
-- Час довільний, не прив'язаний до дзвінків; заняття показуються й на канікулах.
--
-- Формат (той самий у вебі й Android):
--   activities [{"t":"Футбол","e":"⚽","d":[2,4],"a":"16:00","b":"17:30","p":"Стадіон","n":"форма, бутси"}, ...]
--     t — назва (1..60), e — емодзі (1..16), d — дні тижня 1..7 (1 = понеділок), без повторів,
--     a / b — початок і кінець «H:MM» (кінець пізніше), p — місце (≤80, необов'язково), n — що взяти (≤120, необов'язково).
--   До 20 занять.
--
-- Старі клієнти (Android ≤ 0.2.7, кешований веб) пишуть upsert без цієї колонки — PostgREST оновлює лише
-- передані стовпці, тож заняття не стираються.

create or replace function public.school_activities_valid(activities jsonb)
returns boolean
language sql
immutable
set search_path to ''
as $$
  -- CASE, а не and/or: SQL не гарантує порядку обчислення, а jsonb_array_length чи ::time на поганих даних
  -- кинули б виняток замість «false».
  select case when jsonb_typeof(activities) <> 'array' or jsonb_array_length(activities) > 20 then false
    else not exists (
      select 1 from jsonb_array_elements(activities) x
      where case
        when jsonb_typeof(x) <> 'object' then true
        when jsonb_typeof(x->'t') is distinct from 'string' or char_length(x->>'t') not between 1 and 60 then true
        when jsonb_typeof(x->'e') is distinct from 'string' or char_length(x->>'e') not between 1 and 16 then true
        when jsonb_typeof(x->'a') is distinct from 'string' or (x->>'a') !~ '^([01]?[0-9]|2[0-3]):[0-5][0-9]$' then true
        when jsonb_typeof(x->'b') is distinct from 'string' or (x->>'b') !~ '^([01]?[0-9]|2[0-3]):[0-5][0-9]$' then true
        when (x->>'b')::time <= (x->>'a')::time then true
        when x ? 'p' and jsonb_typeof(x->'p') not in ('string', 'null') then true
        when char_length(coalesce(x->>'p', '')) > 80 then true
        when x ? 'n' and jsonb_typeof(x->'n') not in ('string', 'null') then true
        when char_length(coalesce(x->>'n', '')) > 120 then true
        when jsonb_typeof(x->'d') is distinct from 'array' then true
        when jsonb_array_length(x->'d') not between 1 and 7 then true
        when exists (select 1 from jsonb_array_elements(x->'d') d where d::text !~ '^[1-7]$') then true
        else (select count(distinct d::text) from jsonb_array_elements(x->'d') d) <> jsonb_array_length(x->'d')
      end)
  end
$$;

alter table public.school_timetables
  add column activities jsonb not null default '[]'::jsonb,
  add constraint school_timetables_activities_valid check (public.school_activities_valid(activities));

-- Обмеження розміру тепер рахує й заняття.
alter table public.school_timetables drop constraint school_timetables_size;
alter table public.school_timetables add constraint school_timetables_size
  check (pg_column_size(week) + pg_column_size(bells) + pg_column_size(holidays) + pg_column_size(activities) < 32768);

-- Права на school_timetables видано на всю таблицю, тож нова колонка їх уже має; явно — на випадок,
-- якщо колись перейдемо на права по стовпцях (як у school_families).
grant select (activities), insert (activities), update (activities) on public.school_timetables to authenticated;
