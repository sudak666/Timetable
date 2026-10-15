-- Щоденне нагадування про домашку на завтра (нд–чт, 16:00 UTC ≈ 19:00 за Києвом влітку / 18:00 взимку).
create extension if not exists pg_cron;

create or replace function public.school_hw_remind()
returns void language plpgsql security definer set search_path = '' as $$
begin
  perform net.http_post(
    url := 'https://vkwkyhjjjmcpmiakxohw.supabase.co/functions/v1/school-push',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-hook-secret', (select v from public.school_secrets where k = 'hook_secret')),
    body := jsonb_build_object('table', 'hw_remind', 'type', 'CRON'));
end $$;
revoke execute on function public.school_hw_remind() from public, anon, authenticated;

select cron.schedule('school-hw-remind', '0 16 * * 0-4', 'select public.school_hw_remind()');
