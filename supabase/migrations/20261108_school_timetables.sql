-- Свій розклад для кожної дитини: дзвінки, уроки по днях, канікули.
-- Один рядок на дитину (документ). Редагують батьки або сама дитина; видаляють лише батьки.
--
-- Формат (той самий у вебі й Android):
--   bells    [["8:30","9:15"], ...]                         — до 12 уроків на день
--   week     [[{"s":"Математика","o":false}, ...], ... ]    — 5..7 днів, з понеділка; o = факультатив
--   holidays [["2026-10-26","2026-11-01"], ...]             — перший і останній день включно

create or replace function public.school_timetable_valid(bells jsonb, week jsonb, holidays jsonb)
returns boolean
language sql
immutable
set search_path to ''
as $$
  select
    jsonb_typeof(bells) = 'array'
    and jsonb_array_length(bells) between 1 and 12
    and not exists (
      select 1 from jsonb_array_elements(bells) b
      where jsonb_typeof(b) <> 'array' or jsonb_array_length(b) <> 2
         or (b->>0) !~ '^([01]?[0-9]|2[0-3]):[0-5][0-9]$'
         or (b->>1) !~ '^([01]?[0-9]|2[0-3]):[0-5][0-9]$')
    and jsonb_typeof(week) = 'array'
    and jsonb_array_length(week) between 5 and 7
    and not exists (
      select 1 from jsonb_array_elements(week) d
      where jsonb_typeof(d) <> 'array' or jsonb_array_length(d) > jsonb_array_length(bells))
    and not exists (
      select 1 from jsonb_array_elements(week) d, jsonb_array_elements(d) l
      where jsonb_typeof(l) <> 'object'
         or jsonb_typeof(l->'s') <> 'string'
         or char_length(l->>'s') not between 1 and 60
         or (l ? 'o' and jsonb_typeof(l->'o') <> 'boolean'))
    and jsonb_typeof(holidays) = 'array'
    and jsonb_array_length(holidays) <= 30
    and not exists (
      select 1 from jsonb_array_elements(holidays) h
      where jsonb_typeof(h) <> 'array' or jsonb_array_length(h) <> 2
         or (h->>0) !~ '^\d{4}-\d{2}-\d{2}$'
         or (h->>1) !~ '^\d{4}-\d{2}-\d{2}$'
         or (h->>0) > (h->>1))
$$;

create table public.school_timetables (
  child_id   uuid primary key,
  family_id  uuid not null,
  bells      jsonb not null,
  week       jsonb not null,
  holidays   jsonb not null default '[]'::jsonb,
  updated_at timestamptz not null default now(),
  constraint school_timetables_member_fkey foreign key (family_id, child_id)
    references public.school_members (family_id, user_id) on delete cascade,
  constraint school_timetables_valid check (public.school_timetable_valid(bells, week, holidays)),
  constraint school_timetables_size check (pg_column_size(week) + pg_column_size(bells) + pg_column_size(holidays) < 32768)
);

create index school_timetables_family_idx on public.school_timetables (family_id);

alter table public.school_timetables enable row level security;

create policy tt_sel on public.school_timetables for select
  using (public.school_role(family_id) is not null);

-- Лише для дітей цієї сім'ї: батько або сама дитина.
create policy tt_ins on public.school_timetables for insert
  with check (
    exists (select 1 from public.school_members m
            where m.family_id = school_timetables.family_id and m.user_id = school_timetables.child_id and m.role = 'child')
    and (public.school_role(family_id) = 'parent' or child_id = (select auth.uid())));

create policy tt_upd on public.school_timetables for update
  using (public.school_role(family_id) = 'parent' or child_id = (select auth.uid()))
  with check (public.school_role(family_id) = 'parent' or child_id = (select auth.uid()));

create policy tt_del on public.school_timetables for delete
  using (public.school_role(family_id) = 'parent');

-- updated_at ставить сервер — клієнтам не можна підробляти час.
create or replace function public.school_timetables_touch()
returns trigger
language plpgsql
set search_path to ''
as $$
begin
  new.updated_at := now();
  return new;
end $$;

create trigger school_timetables_touch before insert or update on public.school_timetables
  for each row execute function public.school_timetables_touch();

-- Перенесення: наявні діти отримують розклад, який раніше був зашитий у код.
insert into public.school_timetables (child_id, family_id, bells, week, holidays)
select m.user_id, m.family_id,
  '[["8:30","9:15"],["9:25","10:10"],["10:30","11:15"],["11:25","12:10"],["12:30","13:15"],["13:35","14:20"],["14:30","15:15"]]'::jsonb,
  '[
    [{"s":"Математика"},{"s":"Всесв. історія"},{"s":"Література"},{"s":"Англ. мова"},{"s":"Укр. мова"},{"s":"STEM"},{"s":"Математика","o":true}],
    [{"s":"Література"},{"s":"Інформатика"},{"s":"Укр. мова"},{"s":"Географія"},{"s":"Математика"},{"s":"Фіз-ра"}],
    [{"s":"Англ. мова"},{"s":"Математика"},{"s":"Технології"},{"s":"Укр. мова"},{"s":"Громад. освіта"},{"s":"Довкілля"},{"s":"STEM"}],
    [{"s":"Математика"},{"s":"Укр. мова"},{"s":"Географія"},{"s":"Фіз-ра"},{"s":"Англ. мова"},{"s":"Мистецтво"},{"s":"ЗБД"}],
    [{"s":"Всесв. історія"},{"s":"Англ. мова"},{"s":"Література"},{"s":"Фіз-ра"},{"s":"Довкілля"},{"s":"Математика"}]
  ]'::jsonb,
  '[["2026-10-26","2026-11-01"],["2026-12-24","2027-01-10"],["2027-03-22","2027-03-28"],["2027-05-29","2027-08-31"]]'::jsonb
from public.school_members m
where m.role = 'child'
on conflict (child_id) do nothing;

alter publication supabase_realtime add table public.school_timetables;
