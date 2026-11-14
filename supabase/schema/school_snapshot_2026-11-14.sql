-- ЗНІМОК СХЕМИ «Дванадцятки» (таблиці school_*) з продакшн-бази — 14.11.2026,
-- доповнений міграцією 20261115_school_audit_fixes.sql (застосовано 15.11.2026).
--
-- Навіщо: частина схеми (school_rewards_init, school_rewards_codes, school_hide_parent_code,
-- school_features_v2, school_push_triggers, school_avatar_whitelist, school_grades_allow_pass,
-- 25.09–07.10.2026) застосовувалась без файлів у репозиторії. Тут — повний поточний стан:
-- таблиці, обмеження, індекси, функції, тригери, права й політики RLS (аудит 14.11.2026, В8).
--
-- Знято лише читанням каталогу (pg_get_*_def, pg_policies, information_schema). Дані й секрети
-- (school_secrets.v) сюди не потрапили.
--
-- НЕ ЗАПУСКАТИ на живій базі — там усе це вже є. Для нової чи локальної бази: цей файл замість
-- усіх міграцій до 20261115 включно; довідник місць тривог — supabase/scripts/alert_locations.py;
-- cron і секрети — див. кінець файлу. Після кожної зміни схеми в продакшні цей файл оновлювати.
--
-- Потрібні розширення: pgcrypto (extensions.gen_random_bytes), pg_net (net.http_post), pg_cron.

-- ───────────────────────────── функції без залежностей від таблиць ─────────────────────────────

CREATE OR REPLACE FUNCTION public.school_new_code(n integer)
 RETURNS text
 LANGUAGE sql
 SET search_path TO ''
AS $function$
  select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', (get_byte(r.b, i) % 32) + 1, 1), '' order by i)
  from (select extensions.gen_random_bytes(n) as b) r, generate_series(0, n - 1) as i
$function$;

CREATE OR REPLACE FUNCTION public.school_timetable_valid(bells jsonb, week jsonb, holidays jsonb)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO ''
AS $function$
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
$function$;

CREATE OR REPLACE FUNCTION public.school_activities_valid(activities jsonb)
 RETURNS boolean
 LANGUAGE sql
 IMMUTABLE
 SET search_path TO ''
AS $function$
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
$function$;

-- ───────────────────────────── таблиці ─────────────────────────────

create table public.school_alert_locations (
  uid integer not null,
  name text not null,
  kind text not null,
  oblast_uid integer,
  raion_uid integer,
  separate boolean not null default false
);

create table public.school_alert_state (
  uid integer not null,
  status text not null,
  since timestamp with time zone not null,
  checked_at timestamp with time zone not null
);

create table public.school_families (
  id uuid not null default gen_random_uuid(),
  name text not null default 'Сім''я'::text,
  invite_code text not null default public.school_new_code(8),
  rates jsonb not null default '{"1": -300, "2": -200, "3": -150, "4": -100, "5": -70, "6": 0, "7": 70, "8": 100, "9": 150, "10": 200, "11": 250, "12": 300}'::jsonb,
  created_at timestamp with time zone not null default now(),
  parent_code text not null default public.school_new_code(10),
  alert_uid integer
);

create table public.school_members (
  family_id uuid not null,
  user_id uuid not null,
  role text not null,
  name text not null default ''::text,
  goal_title text not null default ''::text,
  goal_amount integer not null default 0,
  avatar text not null default ''::text,
  theme text not null default ''::text
);

create table public.school_grades (
  id uuid not null default gen_random_uuid(),
  family_id uuid not null,
  child_id uuid not null,
  subject text not null,
  grade integer not null,
  date date not null default CURRENT_DATE,
  status text not null default 'pending'::text,
  amount integer,
  created_at timestamp with time zone not null default now()
);

create table public.school_ledger (
  id uuid not null default gen_random_uuid(),
  family_id uuid not null,
  child_id uuid not null,
  kind text not null,
  amount integer not null,
  note text not null default ''::text,
  date date not null default CURRENT_DATE,
  created_at timestamp with time zone not null default now()
);

create table public.school_challenges (
  id uuid not null default gen_random_uuid(),
  family_id uuid not null,
  child_id uuid not null,
  title text not null,
  subject text,
  min_grade integer not null default 10,
  need integer not null default 1,
  reward integer not null,
  start_date date not null default CURRENT_DATE,
  end_date date not null,
  created_at timestamp with time zone not null default now()
);

create table public.school_homework (
  id uuid not null default gen_random_uuid(),
  family_id uuid not null,
  child_id uuid not null,
  subject text not null,
  due date not null,
  text text not null,
  done boolean not null default false,
  created_at timestamp with time zone not null default now(),
  created_by uuid default auth.uid()
);

create table public.school_timetables (
  child_id uuid not null,
  family_id uuid not null,
  bells jsonb not null,
  week jsonb not null,
  holidays jsonb not null default '[]'::jsonb,
  updated_at timestamp with time zone not null default now(),
  activities jsonb not null default '[]'::jsonb
);

create table public.school_push (
  endpoint text not null,
  user_id uuid not null,
  sub jsonb not null,
  created_at timestamp with time zone not null default now()
);

create table public.school_fcm_tokens (
  token text not null,
  user_id uuid not null,
  platform text not null default 'android'::text,
  updated_at timestamp with time zone not null default now(),
  alerts boolean not null default false
);

create table public.school_join_attempts (
  user_id uuid not null,
  at timestamp with time zone not null default now()
);

create table public.school_secrets (
  k text not null,
  v text not null
);

-- ───────────────────────────── обмеження ─────────────────────────────

alter table public.school_alert_locations add constraint school_alert_locations_pkey PRIMARY KEY (uid);
alter table public.school_alert_locations add constraint school_alert_locations_kind_check CHECK ((kind = ANY (ARRAY['oblast'::text, 'city'::text, 'raion'::text, 'hromada'::text])));
alter table public.school_alert_locations add constraint school_alert_locations_name_check CHECK (((char_length(name) >= 1) AND (char_length(name) <= 200)));
alter table public.school_alert_locations add constraint school_alert_locations_tree CHECK ((((kind = ANY (ARRAY['oblast'::text, 'city'::text])) AND (oblast_uid IS NULL) AND (raion_uid IS NULL)) OR ((kind = 'raion'::text) AND (oblast_uid IS NOT NULL) AND (raion_uid IS NULL)) OR ((kind = 'hromada'::text) AND (oblast_uid IS NOT NULL) AND (raion_uid IS NOT NULL))));
alter table public.school_alert_locations add constraint school_alert_locations_oblast_uid_fkey FOREIGN KEY (oblast_uid) REFERENCES public.school_alert_locations(uid);
alter table public.school_alert_locations add constraint school_alert_locations_raion_uid_fkey FOREIGN KEY (raion_uid) REFERENCES public.school_alert_locations(uid);
alter table public.school_alert_state add constraint school_alert_state_pkey PRIMARY KEY (uid);
alter table public.school_alert_state add constraint school_alert_state_status_check CHECK ((status = ANY (ARRAY['A'::text, 'P'::text, 'N'::text])));
alter table public.school_alert_state add constraint school_alert_state_uid_fkey FOREIGN KEY (uid) REFERENCES public.school_alert_locations(uid) ON DELETE CASCADE;

alter table public.school_families add constraint school_families_pkey PRIMARY KEY (id);
alter table public.school_families add constraint school_families_invite_code_key UNIQUE (invite_code);
alter table public.school_families add constraint school_families_parent_code_key UNIQUE (parent_code);
alter table public.school_families add constraint school_families_alert_uid_fkey FOREIGN KEY (alert_uid) REFERENCES public.school_alert_locations(uid) ON DELETE SET NULL;

alter table public.school_members add constraint school_members_pkey PRIMARY KEY (family_id, user_id);
alter table public.school_members add constraint school_members_avatar_check CHECK ((length(avatar) <= 40));
alter table public.school_members add constraint school_members_avatar_chk CHECK ((avatar = ANY (ARRAY[''::text, '🦊'::text, '🐼'::text, '🐸'::text, '🐙'::text, '🐯'::text, '🦁'::text, '🐺'::text, '🦉'::text, '🐧'::text, '🦄'::text, '🤖'::text, '👽'::text, '🦖'::text, '🐲'::text, '👾'::text, '🦈'::text])));
alter table public.school_members add constraint school_members_goal_amount_check CHECK (((goal_amount >= 0) AND (goal_amount <= 1000000)));
alter table public.school_members add constraint school_members_goal_title_check CHECK ((length(goal_title) <= 80));
alter table public.school_members add constraint school_members_role_check CHECK ((role = ANY (ARRAY['parent'::text, 'child'::text])));
alter table public.school_members add constraint school_members_theme_check CHECK ((length(theme) <= 20));
alter table public.school_members add constraint school_members_theme_chk CHECK ((theme ~ '^(#[0-9a-fA-F]{6})?$'::text));
alter table public.school_members add constraint school_members_family_id_fkey FOREIGN KEY (family_id) REFERENCES public.school_families(id) ON DELETE CASCADE;
alter table public.school_members add constraint school_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

alter table public.school_grades add constraint school_grades_pkey PRIMARY KEY (id);
alter table public.school_grades add constraint school_grades_grade_check CHECK (((grade >= 0) AND (grade <= 12)));
alter table public.school_grades add constraint school_grades_status_check CHECK ((status = ANY (ARRAY['pending'::text, 'approved'::text])));
alter table public.school_grades add constraint school_grades_subject_check CHECK (((length(subject) >= 1) AND (length(subject) <= 60)));
alter table public.school_grades add constraint school_grades_child_id_fkey FOREIGN KEY (child_id) REFERENCES auth.users(id) ON DELETE CASCADE;
alter table public.school_grades add constraint school_grades_family_id_fkey FOREIGN KEY (family_id) REFERENCES public.school_families(id) ON DELETE CASCADE;

alter table public.school_ledger add constraint school_ledger_pkey PRIMARY KEY (id);
alter table public.school_ledger add constraint school_ledger_kind_check CHECK ((kind = ANY (ARRAY['bonus'::text, 'payout'::text])));
alter table public.school_ledger add constraint school_ledger_note_check CHECK ((length(note) <= 200));
alter table public.school_ledger add constraint school_ledger_child_id_fkey FOREIGN KEY (child_id) REFERENCES auth.users(id) ON DELETE CASCADE;
alter table public.school_ledger add constraint school_ledger_family_id_fkey FOREIGN KEY (family_id) REFERENCES public.school_families(id) ON DELETE CASCADE;

alter table public.school_challenges add constraint school_challenges_pkey PRIMARY KEY (id);
alter table public.school_challenges add constraint school_challenges_min_grade_check CHECK (((min_grade >= 1) AND (min_grade <= 12)));
alter table public.school_challenges add constraint school_challenges_need_check CHECK (((need >= 1) AND (need <= 50)));
alter table public.school_challenges add constraint school_challenges_reward_check CHECK (((reward >= 0) AND (reward <= 100000)));
alter table public.school_challenges add constraint school_challenges_subject_check CHECK ((length(subject) <= 60));
alter table public.school_challenges add constraint school_challenges_title_check CHECK (((length(title) >= 1) AND (length(title) <= 120)));
alter table public.school_challenges add constraint school_challenges_child_id_fkey FOREIGN KEY (child_id) REFERENCES auth.users(id) ON DELETE CASCADE;
alter table public.school_challenges add constraint school_challenges_family_id_fkey FOREIGN KEY (family_id) REFERENCES public.school_families(id) ON DELETE CASCADE;

alter table public.school_homework add constraint school_homework_pkey PRIMARY KEY (id);
alter table public.school_homework add constraint school_homework_subject_check CHECK (((length(subject) >= 1) AND (length(subject) <= 60)));
alter table public.school_homework add constraint school_homework_text_check CHECK (((length(text) >= 1) AND (length(text) <= 500)));
alter table public.school_homework add constraint school_homework_child_id_fkey FOREIGN KEY (child_id) REFERENCES auth.users(id) ON DELETE CASCADE;
alter table public.school_homework add constraint school_homework_family_id_fkey FOREIGN KEY (family_id) REFERENCES public.school_families(id) ON DELETE CASCADE;
alter table public.school_homework add constraint school_homework_created_by_fkey FOREIGN KEY (created_by) REFERENCES auth.users(id) ON DELETE SET NULL;

alter table public.school_timetables add constraint school_timetables_pkey PRIMARY KEY (child_id);
alter table public.school_timetables add constraint school_timetables_activities_valid CHECK (public.school_activities_valid(activities));
alter table public.school_timetables add constraint school_timetables_size CHECK (((((pg_column_size(week) + pg_column_size(bells)) + pg_column_size(holidays)) + pg_column_size(activities)) < 32768));
alter table public.school_timetables add constraint school_timetables_valid CHECK (public.school_timetable_valid(bells, week, holidays));
alter table public.school_timetables add constraint school_timetables_member_fkey FOREIGN KEY (family_id, child_id) REFERENCES public.school_members(family_id, user_id) ON DELETE CASCADE;

alter table public.school_push add constraint school_push_pkey PRIMARY KEY (endpoint);
alter table public.school_push add constraint school_push_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

alter table public.school_fcm_tokens add constraint school_fcm_tokens_pkey PRIMARY KEY (token);
alter table public.school_fcm_tokens add constraint school_fcm_tokens_platform_check CHECK ((platform = 'android'::text));
alter table public.school_fcm_tokens add constraint school_fcm_tokens_token_check CHECK (((char_length(token) >= 20) AND (char_length(token) <= 4096)));
alter table public.school_fcm_tokens add constraint school_fcm_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

alter table public.school_join_attempts add constraint school_join_attempts_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;

alter table public.school_secrets add constraint school_secrets_pkey PRIMARY KEY (k);

-- ───────────────────────────── індекси ─────────────────────────────

CREATE INDEX school_alert_locations_oblast_idx ON public.school_alert_locations USING btree (oblast_uid);
CREATE INDEX school_alert_locations_raion_idx ON public.school_alert_locations USING btree (raion_uid);
CREATE INDEX school_challenges_child_id_idx ON public.school_challenges USING btree (child_id);
CREATE INDEX school_challenges_family_id_child_id_idx ON public.school_challenges USING btree (family_id, child_id);
CREATE INDEX school_families_alert_uid_idx ON public.school_families USING btree (alert_uid) WHERE (alert_uid IS NOT NULL);
CREATE INDEX school_fcm_tokens_user_idx ON public.school_fcm_tokens USING btree (user_id);
CREATE INDEX school_fcm_tokens_alerts_idx ON public.school_fcm_tokens USING btree (user_id) WHERE alerts;
CREATE INDEX school_grades_child_id_idx ON public.school_grades USING btree (child_id);
CREATE INDEX school_grades_family_id_child_id_idx ON public.school_grades USING btree (family_id, child_id);
CREATE INDEX school_homework_child_id_idx ON public.school_homework USING btree (child_id);
CREATE INDEX school_homework_created_by_idx ON public.school_homework USING btree (created_by);
CREATE INDEX school_homework_family_id_child_id_due_idx ON public.school_homework USING btree (family_id, child_id, due);
CREATE INDEX school_join_attempts_user_at_idx ON public.school_join_attempts USING btree (user_id, at);
CREATE INDEX school_ledger_child_id_idx ON public.school_ledger USING btree (child_id);
CREATE INDEX school_ledger_family_id_child_id_idx ON public.school_ledger USING btree (family_id, child_id);
CREATE INDEX school_members_user_id_idx ON public.school_members USING btree (user_id);
CREATE INDEX school_push_user_id_idx ON public.school_push USING btree (user_id);
CREATE INDEX school_timetables_family_child_idx ON public.school_timetables USING btree (family_id, child_id);

-- ───────────────────────────── функції, що працюють із таблицями ─────────────────────────────

CREATE OR REPLACE FUNCTION public.school_role(fid uuid)
 RETURNS text
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$ select role from public.school_members where family_id=fid and user_id=(select auth.uid()) $function$;

CREATE OR REPLACE FUNCTION public.school_parent_code(fid uuid)
 RETURNS text
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$ select parent_code from public.school_families where id=fid and public.school_role(fid)='parent' $function$;

CREATE OR REPLACE FUNCTION public.school_create_family(p_name text, p_parent_name text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare fid uuid;
begin
  if auth.uid() is null then raise exception 'not signed in'; end if;
  insert into public.school_families(name) values(coalesce(nullif(trim(p_name),''),'Сім''я')) returning id into fid;
  insert into public.school_members values(fid,auth.uid(),'parent',coalesce(trim(p_parent_name),''));
  return fid;
end $function$;

CREATE OR REPLACE FUNCTION public.school_join_family(p_code text, p_name text)
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  uid uuid := (select auth.uid());
  c text := upper(trim(coalesce(p_code, '')));
  fid uuid;
  r text;
begin
  if uid is null then
    raise exception 'not signed in';
  end if;

  delete from public.school_join_attempts where user_id = uid and at < now() - interval '1 day';
  if (select count(*) from public.school_join_attempts where user_id = uid and at > now() - interval '1 hour') >= 10 then
    raise exception 'Забагато спроб — спробуй через годину';
  end if;

  if c <> '' then
    select id, 'child' into fid, r from public.school_families where invite_code = c;
    if fid is null then
      select id, 'parent' into fid, r from public.school_families where parent_code = c;
    end if;
  end if;

  if fid is null then
    -- Не виняток: інакше відкотиться й запис спроби. Клієнт показує «Невірний код».
    insert into public.school_join_attempts (user_id) values (uid);
    return null;
  end if;

  insert into public.school_members values (fid, uid, r, coalesce(trim(p_name), ''))
  on conflict (family_id, user_id) do nothing;
  return fid;
end $function$;

CREATE OR REPLACE FUNCTION public.school_rotate_invite_code(fid uuid)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  code text;
begin
  if public.school_role(fid) is distinct from 'parent' then
    raise exception 'Змінити код можуть лише батьки';
  end if;
  loop
    begin
      update public.school_families set invite_code = public.school_new_code(8) where id = fid
      returning invite_code into code;
      return code;
    exception when unique_violation then
      -- Збіг із чужим кодом (шанс ~10⁻¹²) — пробуємо ще раз.
    end;
  end loop;
end $function$;

CREATE OR REPLACE FUNCTION public.school_delete_account()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
declare
  uid uuid := (select auth.uid());
begin
  if uid is null then
    raise exception 'not signed in';
  end if;

  delete from public.school_families f
  where exists (
          select 1 from public.school_members m
          where m.family_id = f.id and m.user_id = uid and m.role = 'parent')
    and not exists (
          select 1 from public.school_members m
          where m.family_id = f.id and m.role = 'parent' and m.user_id <> uid);

  delete from auth.users where id = uid;
end $function$;

CREATE OR REPLACE FUNCTION public.school_register_fcm(p_token text, p_alerts boolean DEFAULT false)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
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
end $function$;

CREATE OR REPLACE FUNCTION public.school_timetables_touch()
 RETURNS trigger
 LANGUAGE plpgsql
 SET search_path TO ''
AS $function$
begin
  new.updated_at := now();
  return new;
end $function$;

-- Push і cron: виклик edge-функції school-push із секретом school_secrets.hook_secret.
CREATE OR REPLACE FUNCTION public.school_push_hook()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  perform net.http_post(
    url:='https://vkwkyhjjjmcpmiakxohw.supabase.co/functions/v1/school-push',
    headers:=jsonb_build_object('Content-Type','application/json','x-hook-secret',(select v from public.school_secrets where k='hook_secret')),
    body:=jsonb_build_object('table',TG_TABLE_NAME,'type',TG_OP,'record',to_jsonb(NEW),'old_record',case when TG_OP='UPDATE' then to_jsonb(OLD) end));
  return NEW;
exception when others then return NEW;
end $function$;

CREATE OR REPLACE FUNCTION public.school_alerts_tick()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if not exists (select 1 from public.school_families where alert_uid is not null) then
    return;
  end if;
  perform net.http_post(
    url := 'https://vkwkyhjjjmcpmiakxohw.supabase.co/functions/v1/school-push',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-hook-secret', (select v from public.school_secrets where k = 'hook_secret')),
    body := jsonb_build_object('table', 'alerts', 'type', 'CRON'));
end $function$;

-- Нагадування про домашку: cron о 15 і 16 UTC, спрацьовує лише о 18-й за Києвом (і взимку, і влітку).
CREATE OR REPLACE FUNCTION public.school_hw_remind()
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO ''
AS $function$
begin
  if extract(hour from (now() at time zone 'Europe/Kyiv')) <> 18 then
    return;
  end if;
  perform net.http_post(
    url := 'https://vkwkyhjjjmcpmiakxohw.supabase.co/functions/v1/school-push',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-hook-secret', (select v from public.school_secrets where k = 'hook_secret')),
    body := jsonb_build_object('table', 'hw_remind', 'type', 'CRON'));
end $function$;

-- Приватний Realtime-канал сім'ї «fam-<uuid>»: лише учасники.
CREATE OR REPLACE FUNCTION public.school_realtime_member(topic text)
 RETURNS boolean
 LANGUAGE sql
 STABLE SECURITY DEFINER
 SET search_path TO ''
AS $function$
  select case
    when topic ~ '^fam-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      then public.school_role(substring(topic from 5)::uuid) is not null
    else false
  end
$function$;

-- ───────────────────────────── тригери ─────────────────────────────

CREATE TRIGGER school_challenges_push AFTER INSERT ON public.school_challenges FOR EACH ROW EXECUTE FUNCTION public.school_push_hook();
CREATE TRIGGER school_grades_push AFTER INSERT OR UPDATE OF status ON public.school_grades FOR EACH ROW EXECUTE FUNCTION public.school_push_hook();
CREATE TRIGGER school_ledger_push AFTER INSERT ON public.school_ledger FOR EACH ROW EXECUTE FUNCTION public.school_push_hook();
CREATE TRIGGER school_members_push AFTER INSERT ON public.school_members FOR EACH ROW EXECUTE FUNCTION public.school_push_hook();
CREATE TRIGGER school_timetables_touch BEFORE INSERT OR UPDATE ON public.school_timetables FOR EACH ROW EXECUTE FUNCTION public.school_timetables_touch();

-- ───────────────────────────── права на функції ─────────────────────────────

revoke all on function public.school_new_code(integer) from public, anon, authenticated;
revoke all on function public.school_alerts_tick() from public, anon, authenticated;
revoke all on function public.school_hw_remind() from public, anon, authenticated;
revoke all on function public.school_push_hook() from public, anon, authenticated;
revoke all on function public.school_create_family(text, text) from public, anon;
grant execute on function public.school_create_family(text, text) to authenticated;
revoke all on function public.school_delete_account() from public, anon;
grant execute on function public.school_delete_account() to authenticated;
revoke all on function public.school_join_family(text, text) from public, anon;
grant execute on function public.school_join_family(text, text) to authenticated;
revoke all on function public.school_parent_code(uuid) from public, anon;
grant execute on function public.school_parent_code(uuid) to authenticated;
revoke all on function public.school_register_fcm(text, boolean) from public, anon;
grant execute on function public.school_register_fcm(text, boolean) to authenticated;
revoke all on function public.school_role(uuid) from public, anon;
grant execute on function public.school_role(uuid) to authenticated;
revoke all on function public.school_rotate_invite_code(uuid) from public, anon;
grant execute on function public.school_rotate_invite_code(uuid) to authenticated;
revoke all on function public.school_realtime_member(text) from public, anon;
grant execute on function public.school_realtime_member(text) to authenticated;

-- ───────────────────────────── права по стовпцях ─────────────────────────────
-- Табличні права anon/authenticated — стандартні Supabase (ALL); нижче — звуження, від яких залежить безпека.

-- parent_code не читається напряму (лише через school_parent_code); змінювати можна лише назву, курс і місце тривог.
revoke select, update on public.school_families from anon, authenticated;
grant select (id, name, invite_code, rates, created_at, alert_uid) on public.school_families to authenticated;
grant update (name, rates, alert_uid) on public.school_families to authenticated;

-- Учасник не може змінити собі роль чи сім'ю.
revoke update on public.school_members from anon, authenticated;
grant update (name, avatar, goal_title, goal_amount, theme) on public.school_members to authenticated;

-- Оцінку не перенести в чужу сім'ю чи іншій дитині.
revoke update on public.school_grades from anon, authenticated;
grant update (subject, grade, date, status, amount) on public.school_grades to authenticated;

revoke update on public.school_homework from anon, authenticated;
grant update (subject, due, text, done) on public.school_homework to authenticated;

-- ───────────────────────────── RLS ─────────────────────────────

alter table public.school_alert_locations enable row level security;
alter table public.school_alert_state enable row level security;
alter table public.school_families enable row level security;
alter table public.school_members enable row level security;
alter table public.school_grades enable row level security;
alter table public.school_ledger enable row level security;
alter table public.school_challenges enable row level security;
alter table public.school_homework enable row level security;
alter table public.school_timetables enable row level security;
alter table public.school_push enable row level security;
alter table public.school_fcm_tokens enable row level security;
alter table public.school_join_attempts enable row level security; -- без політик: лише school_join_family
alter table public.school_secrets enable row level security;       -- без політик: лише service role і security definer

create policy alert_loc_sel on public.school_alert_locations as permissive for select to authenticated
  using (true);
create policy alert_state_sel on public.school_alert_state as permissive for select to authenticated
  using (true);
create policy ch_del on public.school_challenges as permissive for delete to authenticated
  using ((public.school_role(family_id) = 'parent'::text));
create policy ch_ins on public.school_challenges as permissive for insert to authenticated
  with check (public.school_role(family_id) = 'parent'
    and exists (select 1 from public.school_members m
                where m.family_id = school_challenges.family_id and m.user_id = school_challenges.child_id and m.role = 'child'));
create policy ch_sel on public.school_challenges as permissive for select to authenticated
  using ((public.school_role(family_id) IS NOT NULL));
create policy fam_sel on public.school_families as permissive for select to authenticated
  using ((public.school_role(id) IS NOT NULL));
create policy fam_upd on public.school_families as permissive for update to authenticated
  using ((public.school_role(id) = 'parent'::text))
  with check ((public.school_role(id) = 'parent'::text));
create policy fcm_del on public.school_fcm_tokens as permissive for delete to public
  using ((user_id = ( SELECT auth.uid() AS uid)));
create policy fcm_sel on public.school_fcm_tokens as permissive for select to public
  using ((user_id = ( SELECT auth.uid() AS uid)));
create policy gr_del on public.school_grades as permissive for delete to authenticated
  using (((public.school_role(family_id) = 'parent'::text) OR ((child_id = ( SELECT auth.uid() AS uid)) AND (status = 'pending'::text))));
create policy gr_ins on public.school_grades as permissive for insert to authenticated
  with check ((((public.school_role(family_id) = 'parent'::text) AND (EXISTS ( SELECT 1
   FROM public.school_members m
  WHERE ((m.family_id = school_grades.family_id) AND (m.user_id = school_grades.child_id) AND (m.role = 'child'::text))))) OR ((public.school_role(family_id) = 'child'::text) AND (child_id = ( SELECT auth.uid() AS uid)) AND (status = 'pending'::text) AND (amount IS NULL))));
create policy gr_sel on public.school_grades as permissive for select to authenticated
  using ((public.school_role(family_id) IS NOT NULL));
create policy gr_upd on public.school_grades as permissive for update to authenticated
  using ((public.school_role(family_id) = 'parent'::text))
  with check ((public.school_role(family_id) = 'parent'::text));
create policy hw_del on public.school_homework as permissive for delete to authenticated
  using (public.school_role(family_id) = 'parent'
    or (child_id = (select auth.uid()) and (created_by is null or created_by = (select auth.uid()))));
create policy hw_ins on public.school_homework as permissive for insert to authenticated
  with check (((EXISTS ( SELECT 1
   FROM public.school_members m
  WHERE ((m.family_id = school_homework.family_id) AND (m.user_id = school_homework.child_id) AND (m.role = 'child'::text)))) AND ((public.school_role(family_id) = 'parent'::text) OR (child_id = ( SELECT auth.uid() AS uid)))));
create policy hw_sel on public.school_homework as permissive for select to authenticated
  using ((public.school_role(family_id) IS NOT NULL));
create policy hw_upd on public.school_homework as permissive for update to authenticated
  using (((public.school_role(family_id) = 'parent'::text) OR (child_id = ( SELECT auth.uid() AS uid))))
  with check (((public.school_role(family_id) = 'parent'::text) OR (child_id = ( SELECT auth.uid() AS uid))));
create policy lg_del on public.school_ledger as permissive for delete to authenticated
  using ((public.school_role(family_id) = 'parent'::text));
create policy lg_ins on public.school_ledger as permissive for insert to authenticated
  with check (public.school_role(family_id) = 'parent'
    and exists (select 1 from public.school_members m
                where m.family_id = school_ledger.family_id and m.user_id = school_ledger.child_id and m.role = 'child'));
create policy lg_sel on public.school_ledger as permissive for select to authenticated
  using ((public.school_role(family_id) IS NOT NULL));
create policy mem_del on public.school_members as permissive for delete to authenticated
  using (((public.school_role(family_id) = 'parent'::text) AND (user_id <> ( SELECT auth.uid() AS uid))));
create policy mem_sel on public.school_members as permissive for select to authenticated
  using ((public.school_role(family_id) IS NOT NULL));
create policy mem_upd on public.school_members as permissive for update to authenticated
  using (((user_id = ( SELECT auth.uid() AS uid)) OR (public.school_role(family_id) = 'parent'::text)))
  with check (((user_id = ( SELECT auth.uid() AS uid)) OR (public.school_role(family_id) = 'parent'::text)));
create policy push_del on public.school_push as permissive for delete to authenticated
  using ((user_id = ( SELECT auth.uid() AS uid)));
create policy push_ins on public.school_push as permissive for insert to authenticated
  with check ((user_id = ( SELECT auth.uid() AS uid)));
create policy push_sel on public.school_push as permissive for select to authenticated
  using ((user_id = ( SELECT auth.uid() AS uid)));
create policy push_upd on public.school_push as permissive for update to authenticated
  using ((user_id = ( SELECT auth.uid() AS uid)))
  with check ((user_id = ( SELECT auth.uid() AS uid)));
create policy tt_del on public.school_timetables as permissive for delete to public
  using ((public.school_role(family_id) = 'parent'::text));
create policy tt_ins on public.school_timetables as permissive for insert to public
  with check (((EXISTS ( SELECT 1
   FROM public.school_members m
  WHERE ((m.family_id = school_timetables.family_id) AND (m.user_id = school_timetables.child_id) AND (m.role = 'child'::text)))) AND ((public.school_role(family_id) = 'parent'::text) OR (child_id = ( SELECT auth.uid() AS uid)))));
create policy tt_sel on public.school_timetables as permissive for select to public
  using ((public.school_role(family_id) IS NOT NULL));
create policy tt_upd on public.school_timetables as permissive for update to public
  using (((public.school_role(family_id) = 'parent'::text) OR (child_id = ( SELECT auth.uid() AS uid))))
  with check (((public.school_role(family_id) = 'parent'::text) OR (child_id = ( SELECT auth.uid() AS uid))));

-- Realtime: політики на realtime.messages лише для тем «fam-<uuid>».
create policy school_family_read on realtime.messages as permissive for select to authenticated
  using (public.school_realtime_member(realtime.topic()));
create policy school_family_presence on realtime.messages as permissive for insert to authenticated
  with check (realtime.messages.extension = 'presence' and public.school_realtime_member(realtime.topic()));

-- ───────────────────────────── cron і секрети (не SQL-схема — для довідки) ─────────────────────────────
-- select cron.schedule('school-alerts', '* * * * *', 'select public.school_alerts_tick()');
-- select cron.schedule('school-hw-remind', '0 15,16 * * 0-4', 'select public.school_hw_remind()');
-- school_secrets: hook_secret, vapid_public, vapid_private (значення — лише в продакшн-базі).
-- Edge-функція school-push: supabase/functions/school-push (verify_jwt = false, перевіряє x-hook-secret);
-- секрети функції: FCM_SERVICE_ACCOUNT, ALERTS_IN_UA_TOKEN.
