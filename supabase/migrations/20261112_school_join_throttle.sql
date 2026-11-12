-- K1 з аудиту 12.11.2026 (docs/audit-2026-11-12.md): код запрошення не можна перебрати.
--
-- Було: код дитини — 6 hex-символів (16,7 млн варіантів), school_join_family без ліміту спроб,
-- код без заміни, батьки не дізнаються про нового члена сім'ї.
-- Стало:
--   • не більше 10 невдалих спроб за годину на акаунт (school_join_attempts);
--   • нові коди — 8 символів (дитина) / 10 (батьки) з 32-символьного алфавіту без схожих 0/O, 1/I
--     (≈1,1·10¹² і ≈1,1·10¹⁵ варіантів), криптографічна випадковість;
--   • school_rotate_invite_code — батьки міняють код для дитини (старий перестає працювати);
--   • тригер на school_members → push батькам «новий учасник» (обробник у edge-функції school-push).
--
-- Сумісність: невірний код тепер повертає NULL замість винятку — інакше виняток відкотив би й запис
-- невдалої спроби. Існуючі коди НЕ змінюються: учасники лишаються, видані коди працюють.

-- 1. Журнал невдалих спроб (доступ лише з функцій security definer).
create table if not exists public.school_join_attempts (
  user_id uuid not null references auth.users (id) on delete cascade,
  at timestamptz not null default now()
);
create index if not exists school_join_attempts_user_at_idx on public.school_join_attempts (user_id, at);
alter table public.school_join_attempts enable row level security;
revoke all on public.school_join_attempts from public, anon, authenticated;

-- 2. Генератор кодів. 32 символи → байт % 32 без зміщення.
create or replace function public.school_new_code(n integer)
returns text
language sql
volatile
set search_path to ''
as $$
  select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', (get_byte(r.b, i) % 32) + 1, 1), '' order by i)
  from (select extensions.gen_random_bytes(n) as b) r, generate_series(0, n - 1) as i
$$;
revoke all on function public.school_new_code(integer) from public, anon, authenticated;

alter table public.school_families alter column invite_code set default public.school_new_code(8);
alter table public.school_families alter column parent_code set default public.school_new_code(10);

-- 3. Вступ до сім'ї з лімітом спроб.
create or replace function public.school_join_family(p_code text, p_name text)
returns uuid
language plpgsql
security definer
set search_path to ''
as $$
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
end $$;
revoke all on function public.school_join_family(text, text) from public, anon;
grant execute on function public.school_join_family(text, text) to authenticated;

-- 4. Новий код для дитини (лише батьки цієї сім'ї).
create or replace function public.school_rotate_invite_code(fid uuid)
returns text
language plpgsql
security definer
set search_path to ''
as $$
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
end $$;
revoke all on function public.school_rotate_invite_code(uuid) from public, anon;
grant execute on function public.school_rotate_invite_code(uuid) to authenticated;

-- 5. Push батькам про нового учасника (school_push_hook → edge-функція school-push).
drop trigger if exists school_members_push on public.school_members;
create trigger school_members_push
  after insert on public.school_members
  for each row execute function public.school_push_hook();
