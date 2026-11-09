-- Повітряні тривоги (дані alerts.in.ua).
--
-- • Батьки обирають для сім'ї місце: область, район або громаду (school_families.alert_uid).
-- • Раз на хвилину pg_cron кличе school-push (table = 'alerts'): функція бере рядок статусів
--   усієї країни одним запитом, рахує стан для вибраних місць і пише його в school_alert_state.
-- • На початку й наприкінці тривоги — push на пристрої, де увімкнено «тривоги» (school_fcm_tokens.alerts).
-- • Токен alerts.in.ua — лише секрет функції (ALERTS_IN_UA_TOKEN), у застосунки не потрапляє.
-- Довідник місць заповнює наступна міграція (20261110_school_alert_locations.sql).

create table public.school_alert_locations (
  uid        integer primary key,
  name       text not null check (char_length(name) between 1 and 200),
  kind       text not null check (kind in ('oblast', 'city', 'raion', 'hromada')),
  oblast_uid integer references public.school_alert_locations (uid),
  raion_uid  integer references public.school_alert_locations (uid),
  -- Тривоги тут оголошують окремо від області/району (м. Київ, Харків, Запоріжжя).
  separate   boolean not null default false,
  constraint school_alert_locations_tree check (
    (kind in ('oblast', 'city') and oblast_uid is null and raion_uid is null)
    or (kind = 'raion' and oblast_uid is not null and raion_uid is null)
    or (kind = 'hromada' and oblast_uid is not null and raion_uid is not null))
);

create index school_alert_locations_oblast_idx on public.school_alert_locations (oblast_uid);
create index school_alert_locations_raion_idx on public.school_alert_locations (raion_uid);

alter table public.school_alert_locations enable row level security;
-- Публічний довідник: читати може кожен, хто увійшов. Змінює лише міграція.
create policy alert_loc_sel on public.school_alert_locations for select to authenticated using (true);

-- Місце для тривог обирають батьки (наявна політика fam_upd дозволяє оновлення лише батькам).
alter table public.school_families
  add column alert_uid integer references public.school_alert_locations (uid) on delete set null;
create index school_families_alert_uid_idx on public.school_families (alert_uid) where alert_uid is not null;

-- Поточний стан для місць, які вибрала хоч одна сім'я. Пише лише функція (service role).
create table public.school_alert_state (
  uid        integer primary key references public.school_alert_locations (uid) on delete cascade,
  -- A — тривога, P — у частині області/району, N — немає.
  status     text not null check (status in ('A', 'P', 'N')),
  -- Коли почався поточний статус (з точністю до хвилини перевірки).
  since      timestamptz not null,
  -- Остання успішна перевірка — застосунок показує «станом на».
  checked_at timestamptz not null
);

alter table public.school_alert_state enable row level security;
create policy alert_state_sel on public.school_alert_state for select to authenticated using (true);

-- Пристрій сам вирішує, чи хоче push про тривоги (за замовчуванням — ні).
alter table public.school_fcm_tokens add column alerts boolean not null default false;
create index school_fcm_tokens_alerts_idx on public.school_fcm_tokens (user_id) where alerts;

-- Нова сигнатура з прапорцем тривог. Стару прибираємо, щоб виклик лише з p_token
-- (старі версії застосунку) однозначно потрапляв сюди з alerts = false.
drop function public.school_register_fcm(text);

create function public.school_register_fcm(p_token text, p_alerts boolean default false)
returns void
language plpgsql
security definer
set search_path to ''
as $$
declare
  uid uuid := (select auth.uid());
begin
  if uid is null then
    raise exception 'not signed in';
  end if;
  if p_token is null or char_length(p_token) not between 20 and 4096 then
    raise exception 'invalid token';
  end if;
  insert into public.school_fcm_tokens (token, user_id, alerts, updated_at)
  values (p_token, uid, coalesce(p_alerts, false), now())
  on conflict (token) do update set user_id = excluded.user_id, alerts = excluded.alerts, updated_at = now();
end $$;

revoke all on function public.school_register_fcm(text, boolean) from public, anon;
grant execute on function public.school_register_fcm(text, boolean) to authenticated;

-- Щохвилинна перевірка. Якщо жодна сім'я не обрала місце — навіть не будимо функцію.
create or replace function public.school_alerts_tick()
returns void
language plpgsql
security definer
set search_path to ''
as $$
begin
  if not exists (select 1 from public.school_families where alert_uid is not null) then
    return;
  end if;
  perform net.http_post(
    url := 'https://vkwkyhjjjmcpmiakxohw.supabase.co/functions/v1/school-push',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-hook-secret', (select v from public.school_secrets where k = 'hook_secret')),
    body := jsonb_build_object('table', 'alerts', 'type', 'CRON'));
end $$;
revoke execute on function public.school_alerts_tick() from public, anon, authenticated;

select cron.schedule('school-alerts', '* * * * *', 'select public.school_alerts_tick()');
