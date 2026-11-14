-- Виправлення бази за аудитом 14.11.2026 (docs/audit-2026-11-14.md): Б4, Б5, Б6, Б7.
-- Застосовувати лише після «Так, застосуй». Клієнти (Android, сайт), що читають homework.created_by
-- чи заходять у приватний канал, — після цієї міграції.

-- ───────── Б4. Домашку, яку задали батьки, дитина не видаляє ─────────
-- created_by заповнює сам сервер (default auth.uid()); клієнтам нічого надсилати не треба.
-- Старі записи (null) — як раніше: дитина може видалити свої.
alter table public.school_homework
  add column if not exists created_by uuid default auth.uid() references auth.users(id) on delete set null;
create index if not exists school_homework_created_by_idx on public.school_homework (created_by);

drop policy if exists hw_del on public.school_homework;
create policy hw_del on public.school_homework as permissive for delete to authenticated
  using (
    public.school_role(family_id) = 'parent'
    or (child_id = (select auth.uid()) and (created_by is null or created_by = (select auth.uid())))
  );

-- ───────── Б5. Журнал і челенджі — лише для дитини цієї сім'ї (як gr_ins, hw_ins, tt_ins) ─────────
drop policy if exists lg_ins on public.school_ledger;
create policy lg_ins on public.school_ledger as permissive for insert to authenticated
  with check (
    public.school_role(family_id) = 'parent'
    and exists (select 1 from public.school_members m
                where m.family_id = school_ledger.family_id and m.user_id = school_ledger.child_id and m.role = 'child')
  );

drop policy if exists ch_ins on public.school_challenges;
create policy ch_ins on public.school_challenges as permissive for insert to authenticated
  with check (
    public.school_role(family_id) = 'parent'
    and exists (select 1 from public.school_members m
                where m.family_id = school_challenges.family_id and m.user_id = school_challenges.child_id and m.role = 'child')
  );

-- ───────── Б6. Приватний Realtime-канал сім'ї «fam-<uuid>» ─────────
-- Читати (присутність, broadcast) і позначати присутність — лише учасникам цієї сім'ї.
-- Інші застосунки бази не зачіпає: політики стосуються лише тем «fam-<uuid>».
-- CASE — щоб приведення до uuid не падало на чужих темах.
create or replace function public.school_realtime_member(topic text)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select case
    when topic ~ '^fam-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      then public.school_role(substring(topic from 5)::uuid) is not null
    else false
  end
$$;
revoke all on function public.school_realtime_member(text) from public, anon;
grant execute on function public.school_realtime_member(text) to authenticated;

drop policy if exists school_family_read on realtime.messages;
create policy school_family_read on realtime.messages as permissive for select to authenticated
  using (public.school_realtime_member(realtime.topic()));

drop policy if exists school_family_presence on realtime.messages;
create policy school_family_presence on realtime.messages as permissive for insert to authenticated
  with check (realtime.messages.extension = 'presence' and public.school_realtime_member(realtime.topic()));

-- ───────── Б7. Нагадування про домашку — о 18:00 за Києвом і взимку, і влітку ─────────
-- Cron у UTC: о 15:00 і 16:00 (неділя–четвер); функція спрацьовує лише тоді, коли в Києві 18-та година.
create or replace function public.school_hw_remind()
returns void
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if extract(hour from (now() at time zone 'Europe/Kyiv')) <> 18 then
    return;
  end if;
  perform net.http_post(
    url := 'https://vkwkyhjjjmcpmiakxohw.supabase.co/functions/v1/school-push',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-hook-secret', (select v from public.school_secrets where k = 'hook_secret')),
    body := jsonb_build_object('table', 'hw_remind', 'type', 'CRON'));
end $function$;
revoke all on function public.school_hw_remind() from public, anon, authenticated;

select cron.alter_job(job_id := j.jobid, schedule := '0 15,16 * * 0-4')
from cron.job j where j.jobname = 'school-hw-remind';
