-- Видалення акаунта самим користувачем (вимога Google Play та GDPR / ЗУ «Про захист персональних даних»).
--
-- Що видаляється:
--   • акаунт (auth.users) і все, що на нього посилається каскадом: членство в сім'ї,
--     оцінки / бонуси / челенджі / домашка / розклад, де він дитина, push-підписки;
--   • сім'ї, де він ЄДИНИЙ з батьків, — повністю, з даними всіх дітей у ній
--     (без батьків сім'я некерована). Акаунти дітей лишаються: чужі акаунти не видаляємо,
--     діти побачать екран «приєднатися до сім'ї».
-- Якщо в сім'ї є другий з батьків — сім'я лишається йому.
--
-- Перевірено: на auth.users посилаються лише таблиці school_* (інші застосунки проєкту
-- вхід Supabase не використовують), storage-об'єктів користувачів немає.

create or replace function public.school_delete_account()
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

  delete from public.school_families f
  where exists (
          select 1 from public.school_members m
          where m.family_id = f.id and m.user_id = uid and m.role = 'parent')
    and not exists (
          select 1 from public.school_members m
          where m.family_id = f.id and m.role = 'parent' and m.user_id <> uid);

  delete from auth.users where id = uid;
end $$;

-- Лише для тих, хто увійшов; анонімні виклики неможливі.
revoke all on function public.school_delete_account() from public, anon;
grant execute on function public.school_delete_account() to authenticated;
