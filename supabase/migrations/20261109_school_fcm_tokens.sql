-- Токени пристроїв Android (Firebase Cloud Messaging) для push-сповіщень.
-- Web-push лишається в school_push; функція school-push надсилає в обидва канали.
--
-- Один токен = один пристрій. Якщо на тому ж телефоні увійде інший член сім'ї,
-- токен переходить до нього (school_register_fcm), щоб сповіщення не йшли попередньому.

create table public.school_fcm_tokens (
  token      text primary key check (char_length(token) between 20 and 4096),
  user_id    uuid not null references auth.users(id) on delete cascade,
  platform   text not null default 'android' check (platform in ('android')),
  updated_at timestamptz not null default now()
);

create index school_fcm_tokens_user_idx on public.school_fcm_tokens (user_id);

alter table public.school_fcm_tokens enable row level security;

-- Власник бачить і видаляє лише свої токени. Вставка/зміна — тільки через school_register_fcm.
create policy fcm_sel on public.school_fcm_tokens for select using (user_id = (select auth.uid()));
create policy fcm_del on public.school_fcm_tokens for delete using (user_id = (select auth.uid()));

create or replace function public.school_register_fcm(p_token text)
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
  insert into public.school_fcm_tokens (token, user_id, updated_at)
  values (p_token, uid, now())
  on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end $$;

revoke all on function public.school_register_fcm(text) from public, anon;
grant execute on function public.school_register_fcm(text) to authenticated;
