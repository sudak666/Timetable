-- Тести доступу до даних «Дванадцятки» (RLS, права по стовпцях, функції) — pgTAP.
--
-- Запуск: лише на ТИМЧАСОВІЙ базі (CI, .github/workflows/database.yml) після setup.sql і знімка схеми.
-- Усе виконується в одній транзакції й відкочується (ROLLBACK) — у базі нічого не лишається.
--
-- Дійові особи:
--   Сім'я A: p1 (батьки), q (другі батьки, увійшли батьківським кодом), c1 (дитина)
--   Сім'я B: p2 (батьки), c2 (дитина)
--   x — увійшов, але без сім'ї; anon — не увійшов.
-- Як «увійти» користувачем: :as_p1 тощо — ставить JWT-claims (як PostgREST) і SET ROLE authenticated.

\set ON_ERROR_STOP on
\pset pager off
\pset tuples_only on
\pset format unaligned

\set p1 '10000000-0000-4000-8000-0000000000a1'
\set q  '10000000-0000-4000-8000-0000000000a2'
\set c1 '10000000-0000-4000-8000-0000000000a3'
\set p2 '10000000-0000-4000-8000-0000000000b1'
\set c2 '10000000-0000-4000-8000-0000000000b2'
\set x  '10000000-0000-4000-8000-0000000000c1'

\set as_admin 'reset role;'
\set as_anon  'reset role; select tests.claims(null); set local role anon;'
\set as_p1    'reset role; select tests.claims(''10000000-0000-4000-8000-0000000000a1''); set local role authenticated;'
\set as_q     'reset role; select tests.claims(''10000000-0000-4000-8000-0000000000a2''); set local role authenticated;'
\set as_c1    'reset role; select tests.claims(''10000000-0000-4000-8000-0000000000a3''); set local role authenticated;'
\set as_p2    'reset role; select tests.claims(''10000000-0000-4000-8000-0000000000b1''); set local role authenticated;'
\set as_c2    'reset role; select tests.claims(''10000000-0000-4000-8000-0000000000b2''); set local role authenticated;'
\set as_x     'reset role; select tests.claims(''10000000-0000-4000-8000-0000000000c1''); set local role authenticated;'

begin;
set local search_path = public, extensions;

-- Допоміжне: «вхід» як у PostgREST (обидва формати claims, які читає auth.uid()).
create schema tests;
create function tests.claims(uid uuid) returns void
  language plpgsql as $$
begin
  perform set_config('request.jwt.claims',
    case when uid is null then '{"role":"anon"}'
         else json_build_object('sub', uid, 'role', 'authenticated')::text end, true);
  perform set_config('request.jwt.claim.sub', coalesce(uid::text, ''), true);
  perform set_config('request.jwt.claim.role', case when uid is null then 'anon' else 'authenticated' end, true);
end $$;

select no_plan();

-- ───────────────────────────── дані (як адміністратор) ─────────────────────────────

insert into auth.users (id, email, aud, role) values
  (:'p1', 'p1@test.local', 'authenticated', 'authenticated'),
  (:'q',  'q@test.local',  'authenticated', 'authenticated'),
  (:'c1', 'c1@test.local', 'authenticated', 'authenticated'),
  (:'p2', 'p2@test.local', 'authenticated', 'authenticated'),
  (:'c2', 'c2@test.local', 'authenticated', 'authenticated'),
  (:'x',  'x@test.local',  'authenticated', 'authenticated');

insert into public.school_alert_locations (uid, name, kind) values (31, 'м. Київ', 'city');
insert into public.school_alert_state (uid, status, since, checked_at) values (31, 'A', now(), now());
insert into public.school_secrets (k, v) values ('hook_secret', 'test-secret');

-- ───────────────────────────── функції: хто може викликати ─────────────────────────────

select ok(not has_function_privilege('authenticated', 'public.school_new_code(integer)', 'execute'), 'школярі й батьки не викликають school_new_code');
select ok(not has_function_privilege('authenticated', 'public.school_alerts_tick()', 'execute'), 'не викликають school_alerts_tick (лише cron)');
select ok(not has_function_privilege('authenticated', 'public.school_hw_remind()', 'execute'), 'не викликають school_hw_remind (лише cron)');
select ok(not has_function_privilege('authenticated', 'public.school_push_hook()', 'execute'), 'не викликають school_push_hook (лише тригер)');
select ok(not has_function_privilege('anon', 'public.school_create_family(text, text)', 'execute'), 'анонім не створює сім''ю');
select ok(not has_function_privilege('anon', 'public.school_join_family(text, text)', 'execute'), 'анонім не приєднується до сім''ї');
select ok(not has_function_privilege('anon', 'public.school_parent_code(uuid)', 'execute'), 'анонім не бачить батьківський код');
select ok(not has_function_privilege('anon', 'public.school_delete_account()', 'execute'), 'анонім не видаляє акаунт');
select ok(not has_function_privilege('anon', 'public.school_register_fcm(text, boolean)', 'execute'), 'анонім не реєструє FCM');
select ok(not has_function_privilege('anon', 'public.school_rotate_invite_code(uuid)', 'execute'), 'анонім не змінює код');

-- Права по стовпцях, від яких залежить безпека.
select ok(not has_column_privilege('authenticated', 'public.school_families', 'parent_code', 'select'), 'parent_code не читається напряму');
select ok(not has_column_privilege('authenticated', 'public.school_families', 'invite_code', 'update'), 'код запрошення не змінюється напряму (лише ротацією)');
select ok(not has_column_privilege('authenticated', 'public.school_members', 'role', 'update'), 'роль учасника не змінюється');
select ok(not has_column_privilege('authenticated', 'public.school_members', 'family_id', 'update'), 'сім''я учасника не змінюється');
select ok(not has_column_privilege('authenticated', 'public.school_grades', 'family_id', 'update'), 'оцінку не перенести в іншу сім''ю');
select ok(not has_column_privilege('authenticated', 'public.school_grades', 'child_id', 'update'), 'оцінку не перенести іншій дитині');
select ok(not has_column_privilege('authenticated', 'public.school_homework', 'created_by', 'update'), 'автора домашки не підробити');
select ok(not has_table_privilege('anon', 'public.school_families', 'select'), 'анонім не читає сім''ї');

-- ───────────────────────────── сім'ї й вступ ─────────────────────────────

:as_p1
select isnt(school_create_family('Сім''я A', 'Мама'), null, 'p1 створює сім''ю A');
:as_p2
select isnt(school_create_family('Сім''я B', 'Тато'), null, 'p2 створює сім''ю B');

:as_admin
select family_id as fam_a from public.school_members where user_id = :'p1' \gset
select family_id as fam_b from public.school_members where user_id = :'p2' \gset
select invite_code as code_a, parent_code as pcode_a from public.school_families where id = :'fam_a' \gset
select invite_code as code_b from public.school_families where id = :'fam_b' \gset

:as_c1
select is(school_join_family(lower(' ' || :'code_a' || ' '), 'Тарас'), :'fam_a'::uuid, 'c1 вступає в A кодом для дитини (регістр і пробіли не заважають)');
select is(school_role(:'fam_a'), 'child', 'c1 у сім''ї A — дитина');
:as_c2
select is(school_join_family(:'code_b', 'Оля'), :'fam_b'::uuid, 'c2 вступає в B');
:as_q
select is(school_join_family(:'pcode_a', 'Бабуся'), :'fam_a'::uuid, 'q вступає в A батьківським кодом');
select is(school_role(:'fam_a'), 'parent', 'q у сім''ї A — батьки');

-- Перебір кодів: 10 невдалих спроб за годину — далі блок.
:as_x
select is(school_join_family('НЕІСНУЄ', 'X'), null::uuid, 'невірний код — null, а не помилка');
select school_join_family('WRONG' || g, 'X') from generate_series(1, 9) g;
select throws_like($$ select public.school_join_family('ZZZZZZZZ', 'X') $$, 'Забагато спроб%', 'після 10 невдалих спроб — блок на годину');
select is((select count(*)::int from school_join_attempts), 0, 'спроби вступу ніхто не читає (навіть свої)');
select is((select count(*)::int from school_families), 0, 'x без сім''ї не бачить жодної сім''ї');

-- Напряму в сім'ю не вписатись (лише через код).
select throws_like(format($$ insert into public.school_members (family_id, user_id, role) values (%L, %L, 'parent') $$, :'fam_a', :'x'),
  '%row-level security%', 'x не додає себе в чужу сім''ю напряму');

-- Видимість сімей.
:as_p1
select is((select count(*)::int from school_families), 1, 'p1 бачить лише свою сім''ю');
select isnt(school_parent_code(:'fam_a'), null, 'p1 бачить батьківський код своєї сім''ї');
select throws_like($$ select parent_code from public.school_families $$, 'permission denied%', 'parent_code не читається запитом навіть батьками');
:as_c1
select is(school_parent_code(:'fam_a'), null, 'дитина не бачить батьківський код');
select is((select count(*)::int from school_members where family_id = :'fam_a'), 3, 'c1 бачить учасників своєї сім''ї');
:as_p2
select is((select count(*)::int from school_families where id = :'fam_a'), 0, 'p2 не бачить чужу сім''ю');
select is((select count(*)::int from school_members where family_id = :'fam_a'), 0, 'p2 не бачить чужих учасників');
select is(school_parent_code(:'fam_a'), null, 'p2 не бачить чужий батьківський код');
:as_anon
select throws_like($$ select id from public.school_families $$, 'permission denied%', 'анонім не читає сім''ї');
select is((select count(*)::int from school_members), 0, 'анонім не бачить учасників');

-- Зміни сім'ї.
:as_c1
with u as (update school_families set name = 'Злам' where id = :'fam_a' returning 1)
  select is(count(*)::int, 0, 'дитина не перейменовує сім''ю') from u;
select throws_like(format($$ select public.school_rotate_invite_code(%L) $$, :'fam_a'), 'Змінити код можуть лише батьки%', 'дитина не змінює код запрошення');
:as_p2
with u as (update school_families set name = 'Злам', rates = '{}'::jsonb where id = :'fam_a' returning 1)
  select is(count(*)::int, 0, 'чужі батьки не змінюють сім''ю') from u;
select throws_like(format($$ select public.school_rotate_invite_code(%L) $$, :'fam_a'), 'Змінити код можуть лише батьки%', 'чужі батьки не змінюють код запрошення');
:as_p1
select throws_like(format($$ update public.school_families set invite_code = 'AAAAAAAA' where id = %L $$, :'fam_a'),
  'permission denied%', 'код запрошення не задати вручну');
with u as (update school_families set name = 'Сім''я Тараса', alert_uid = 31 where id = :'fam_a' returning 1)
  select is(count(*)::int, 1, 'батьки змінюють назву й місце тривог') from u;
select isnt(school_rotate_invite_code(:'fam_a'), :'code_a', 'батьки змінюють код запрошення');
:as_q
select is(school_join_family(:'code_a', 'Бабуся'), null::uuid, 'старий код після зміни більше не діє');
:as_admin
select is((select name from public.school_families where id = :'fam_a'), 'Сім''я Тараса', 'назву змінили лише батьки');

-- ───────────────────────────── учасники ─────────────────────────────

:as_c1
select throws_like(format($$ update public.school_members set role = 'parent' where user_id = %L $$, :'c1'),
  'permission denied%', 'дитина не робить себе батьками');
select throws_like(format($$ update public.school_members set family_id = %L where user_id = %L $$, :'fam_b', :'c1'),
  'permission denied%', 'дитина не переходить у чужу сім''ю');
with u as (update school_members set name = 'Тарасик', avatar = 'fox', goal_title = 'Велосипед', goal_amount = 6000
           where user_id = :'c1' returning 1)
  select is(count(*)::int, 1, 'дитина змінює своє ім''я, аватар і ціль') from u;
with u as (update school_members set name = 'Злам' where user_id = :'p1' returning 1)
  select is(count(*)::int, 0, 'дитина не змінює профіль батьків') from u;
with u as (delete from school_members where user_id = :'p1' returning 1)
  select is(count(*)::int, 0, 'дитина не видаляє батьків із сім''ї') from u;
:as_p1
with u as (update school_members set goal_amount = 7000 where user_id = :'c1' returning 1)
  select is(count(*)::int, 1, 'батьки змінюють ціль дитини') from u;
with u as (delete from school_members where user_id = :'p1' returning 1)
  select is(count(*)::int, 0, 'батьки не видаляють самі себе (лише видаленням акаунта)') from u;
:as_p2
with u as (update school_members set name = 'Злам' where user_id = :'c1' returning 1)
  select is(count(*)::int, 0, 'чужі батьки не змінюють чужу дитину') from u;
with u as (delete from school_members where user_id = :'c1' returning 1)
  select is(count(*)::int, 0, 'чужі батьки не видаляють чужу дитину') from u;

-- ───────────────────────────── оцінки ─────────────────────────────

:as_c1
select lives_ok(format($$ insert into public.school_grades (family_id, child_id, subject, grade) values (%L, %L, 'Математика', 11) $$, :'fam_a', :'c1'),
  'дитина додає свою оцінку на підтвердження');
select throws_like(format($$ insert into public.school_grades (family_id, child_id, subject, grade, status) values (%L, %L, 'Математика', 12, 'approved') $$, :'fam_a', :'c1'),
  '%row-level security%', 'дитина не ставить собі підтверджену оцінку');
select throws_like(format($$ insert into public.school_grades (family_id, child_id, subject, grade, amount) values (%L, %L, 'Математика', 12, 100000) $$, :'fam_a', :'c1'),
  '%row-level security%', 'дитина не призначає собі суму');
select throws_like(format($$ insert into public.school_grades (family_id, child_id, subject, grade) values (%L, %L, 'Математика', 12) $$, :'fam_b', :'c1'),
  '%row-level security%', 'дитина не пише оцінки в чужу сім''ю');
select throws_like(format($$ insert into public.school_grades (family_id, child_id, subject, grade) values (%L, %L, 'Математика', 1) $$, :'fam_a', :'p1'),
  '%row-level security%', 'дитина не ставить оцінки іншим');
with u as (update school_grades set status = 'approved', amount = 300 where child_id = :'c1' returning 1)
  select is(count(*)::int, 0, 'дитина не підтверджує свої оцінки') from u;

:as_p1
select lives_ok(format($$ insert into public.school_grades (family_id, child_id, subject, grade, status, amount) values (%L, %L, 'Фізика', 10, 'approved', 200) $$, :'fam_a', :'c1'),
  'батьки ставлять дитині підтверджену оцінку');
select throws_like(format($$ insert into public.school_grades (family_id, child_id, subject, grade) values (%L, %L, 'Фізика', 10) $$, :'fam_a', :'c2'),
  '%row-level security%', 'батьки не ставлять оцінку дитині не зі своєї сім''ї');
select throws_like(format($$ insert into public.school_grades (family_id, child_id, subject, grade) values (%L, %L, 'Фізика', 10) $$, :'fam_b', :'c2'),
  '%row-level security%', 'батьки не пишуть оцінки в чужу сім''ю');
select throws_like(format($$ insert into public.school_grades (family_id, child_id, subject, grade) values (%L, %L, 'Фізика', 10) $$, :'fam_a', :'q'),
  '%row-level security%', 'оцінку ставлять лише дитині, не батькам');
with u as (update school_grades set status = 'approved', amount = 250 where child_id = :'c1' and status = 'pending' returning 1)
  select is(count(*)::int, 1, 'батьки підтверджують оцінку дитини') from u;
select throws_like(format($$ update public.school_grades set family_id = %L $$, :'fam_b'), 'permission denied%', 'оцінку не перенести в іншу сім''ю');

:as_c1
select lives_ok(format($$ insert into public.school_grades (family_id, child_id, subject, grade) values (%L, %L, 'Хімія', 9) $$, :'fam_a', :'c1'), 'ще одна оцінка дитини');
with u as (delete from school_grades where child_id = :'c1' and status = 'approved' returning 1)
  select is(count(*)::int, 0, 'дитина не видаляє підтверджені оцінки') from u;
with u as (delete from school_grades where child_id = :'c1' and status = 'pending' returning 1)
  select is(count(*)::int, 1, 'дитина видаляє свою непідтверджену оцінку') from u;

:as_p2
select is((select count(*)::int from school_grades where family_id = :'fam_a'), 0, 'чужі батьки не бачать оцінок');
with u as (delete from school_grades where family_id = :'fam_a' returning 1)
  select is(count(*)::int, 0, 'чужі батьки не видаляють оцінок') from u;
with u as (update school_grades set grade = 1 where family_id = :'fam_a' returning 1)
  select is(count(*)::int, 0, 'чужі батьки не змінюють оцінок') from u;
:as_c2
select is((select count(*)::int from school_grades where family_id = :'fam_a'), 0, 'чужа дитина не бачить оцінок');
:as_anon
select is((select count(*)::int from school_grades), 0, 'анонім не бачить оцінок');
select throws_like(format($$ insert into public.school_grades (family_id, child_id, subject, grade) values (%L, %L, 'Математика', 12) $$, :'fam_a', :'c1'),
  '%row-level security%', 'анонім не додає оцінок');

-- ───────────────────────────── гаманець (бонуси й виплати) ─────────────────────────────

:as_c1
select throws_like(format($$ insert into public.school_ledger (family_id, child_id, kind, amount) values (%L, %L, 'bonus', 100000) $$, :'fam_a', :'c1'),
  '%row-level security%', 'дитина не нараховує собі бонус');
:as_p1
select lives_ok(format($$ insert into public.school_ledger (family_id, child_id, kind, amount, note) values (%L, %L, 'bonus', 100, 'Молодець') $$, :'fam_a', :'c1'),
  'батьки нараховують бонус');
select throws_like(format($$ insert into public.school_ledger (family_id, child_id, kind, amount) values (%L, %L, 'bonus', 100) $$, :'fam_a', :'c2'),
  '%row-level security%', 'батьки не нараховують дитині з іншої сім''ї');
:as_c1
select is((select count(*)::int from school_ledger), 1, 'дитина бачить свій гаманець');
with u as (delete from school_ledger returning 1)
  select is(count(*)::int, 0, 'дитина не видаляє записи гаманця') from u;
with u as (update school_ledger set amount = 100000 returning 1)
  select is(count(*)::int, 0, 'дитина не змінює записи гаманця') from u;
:as_p2
select is((select count(*)::int from school_ledger where family_id = :'fam_a'), 0, 'чужі батьки не бачать гаманця');
with u as (delete from school_ledger where family_id = :'fam_a' returning 1)
  select is(count(*)::int, 0, 'чужі батьки не видаляють записи гаманця') from u;

-- ───────────────────────────── челенджі ─────────────────────────────

:as_c1
select throws_like(format($$ insert into public.school_challenges (family_id, child_id, title, reward, end_date) values (%L, %L, 'Легко', 100000, current_date + 7) $$, :'fam_a', :'c1'),
  '%row-level security%', 'дитина не створює собі челенджі');
:as_p1
select lives_ok(format($$ insert into public.school_challenges (family_id, child_id, title, reward, end_date) values (%L, %L, '3 десятки', 100, current_date + 7) $$, :'fam_a', :'c1'),
  'батьки створюють челендж');
select throws_like(format($$ insert into public.school_challenges (family_id, child_id, title, reward, end_date) values (%L, %L, 'Чужий', 100, current_date + 7) $$, :'fam_a', :'c2'),
  '%row-level security%', 'батьки не створюють челендж дитині з іншої сім''ї');
:as_c1
with u as (delete from school_challenges returning 1)
  select is(count(*)::int, 0, 'дитина не видаляє челенджі') from u;
:as_p2
select is((select count(*)::int from school_challenges where family_id = :'fam_a'), 0, 'чужі батьки не бачать челенджів');

-- ───────────────────────────── домашка ─────────────────────────────

:as_c1
select lives_ok(format($$ insert into public.school_homework (family_id, child_id, subject, due, text) values (%L, %L, 'Математика', current_date + 1, '№ 245') $$, :'fam_a', :'c1'),
  'дитина додає собі домашку');
select throws_like(format($$ insert into public.school_homework (family_id, child_id, subject, due, text) values (%L, %L, 'Математика', current_date + 1, 'чужа') $$, :'fam_b', :'c2'),
  '%row-level security%', 'дитина не додає домашку чужій дитині');
:as_p1
select lives_ok(format($$ insert into public.school_homework (family_id, child_id, subject, due, text) values (%L, %L, 'Фізика', current_date + 1, 'від мами') $$, :'fam_a', :'c1'),
  'батьки додають дитині домашку');
:as_c1
with u as (delete from school_homework where text = 'від мами' returning 1)
  select is(count(*)::int, 0, 'дитина не видаляє домашку від батьків') from u;
with u as (update school_homework set done = true where text = 'від мами' returning 1)
  select is(count(*)::int, 1, 'дитина позначає домашку від батьків зробленою') from u;
select throws_like(format($$ update public.school_homework set created_by = %L $$, :'c1'), 'permission denied%', 'дитина не підробляє автора домашки');
with u as (delete from school_homework where text = '№ 245' returning 1)
  select is(count(*)::int, 1, 'дитина видаляє свою домашку') from u;
:as_p2
select throws_like(format($$ insert into public.school_homework (family_id, child_id, subject, due, text) values (%L, %L, 'Фізика', current_date + 1, 'спам') $$, :'fam_a', :'c1'),
  '%row-level security%', 'чужі батьки не додають домашку');
with u as (update school_homework set text = 'злам' where family_id = :'fam_a' returning 1)
  select is(count(*)::int, 0, 'чужі батьки не змінюють домашку') from u;

-- ───────────────────────────── розклад ─────────────────────────────

:as_c1
select lives_ok(format($$ insert into public.school_timetables (child_id, family_id, bells, week)
  values (%L, %L, '[["08:30","09:15"]]', '[[{"s":"Математика"}],[],[],[],[]]') $$, :'c1', :'fam_a'),
  'дитина зберігає свій розклад');
select throws_like(format($$ insert into public.school_timetables (child_id, family_id, bells, week)
  values (%L, %L, '[["08:30","09:15"]]', '[[{"s":"Злам"}],[],[],[],[]]') $$, :'c2', :'fam_b'),
  '%row-level security%', 'дитина не пише розклад чужій дитині');
:as_p2
select is((select count(*)::int from school_timetables where family_id = :'fam_a'), 0, 'чужі батьки не бачать розкладу');
with u as (update school_timetables set week = '[[],[],[],[],[]]' where family_id = :'fam_a' returning 1)
  select is(count(*)::int, 0, 'чужі батьки не змінюють розклад') from u;
with u as (delete from school_timetables where family_id = :'fam_a' returning 1)
  select is(count(*)::int, 0, 'чужі батьки не видаляють розклад') from u;
:as_c1
with u as (delete from school_timetables returning 1)
  select is(count(*)::int, 0, 'дитина не видаляє розклад (лише батьки)') from u;
:as_p1
with u as (update school_timetables set week = '[[{"s":"Фізика"}],[],[],[],[]]' where child_id = :'c1' returning 1)
  select is(count(*)::int, 1, 'батьки змінюють розклад дитини') from u;

-- ───────────────────────────── push і FCM ─────────────────────────────

:as_c1
select lives_ok(format($$ insert into public.school_push (endpoint, user_id, sub) values ('https://push.test/c1', %L, '{}') $$, :'c1'), 'дитина підписує свій браузер на push');
select throws_like(format($$ insert into public.school_push (endpoint, user_id, sub) values ('https://push.test/fake', %L, '{}') $$, :'p1'),
  '%row-level security%', 'не підписати чужий акаунт на свій браузер');
select lives_ok($$ select public.school_register_fcm(repeat('t', 40), true) $$, 'дитина реєструє свій телефон (FCM)');
select throws_like($$ select public.school_register_fcm('short', true) $$, 'invalid token%', 'занадто короткий FCM-токен відхиляється');
select throws_like(format($$ insert into public.school_fcm_tokens (token, user_id) values (repeat('z', 40), %L) $$, :'p1'),
  '%row-level security%', 'FCM-токени не пишуться напряму');
select is((select count(*)::int from school_fcm_tokens), 1, 'дитина бачить лише свій FCM-токен');
:as_p1
select is((select count(*)::int from school_push), 0, 'батьки не бачать push-підписки дитини');
select is((select count(*)::int from school_fcm_tokens), 0, 'батьки не бачать FCM-токени дитини');
with u as (delete from school_push returning 1)
  select is(count(*)::int, 0, 'батьки не видаляють push-підписки дитини') from u;
:as_anon
select is((select count(*)::int from school_push), 0, 'анонім не бачить push-підписок');
select is((select count(*)::int from school_fcm_tokens), 0, 'анонім не бачить FCM-токенів');

-- ───────────────────────────── службові таблиці й тривоги ─────────────────────────────

:as_p1
select is((select count(*)::int from school_secrets), 0, 'секрети не читаються');
select is((select count(*)::int from school_alert_state), 1, 'стан тривог читається');
:as_c1
with u as (update school_families set alert_uid = null where id = :'fam_a' returning 1)
  select is(count(*)::int, 0, 'дитина не змінює місце тривог') from u;
select throws_like($$ insert into public.school_alert_state (uid, status, since, checked_at) values (31, 'N', now(), now()) $$,
  '%row-level security%', 'стан тривог не підробити');
:as_anon
select is((select count(*)::int from school_alert_state), 0, 'анонім не бачить тривог');
select is((select count(*)::int from school_secrets), 0, 'анонім не бачить секретів');

-- ───────────────────────────── realtime ─────────────────────────────

:as_p1
select ok(school_realtime_member('fam-' || :'fam_a'), 'учасник слухає канал своєї сім''ї');
select ok(not school_realtime_member('fam-' || :'fam_b'), 'не слухає канал чужої сім''ї');
select ok(not school_realtime_member('fam-не-uuid'), 'кривий канал — ні');
select ok(not school_realtime_member('public'), 'загальний канал — ні');

-- ───────────────────────────── видалення акаунта ─────────────────────────────

:as_p1
select lives_ok($$ select public.school_delete_account() $$, 'p1 видаляє свій акаунт');
:as_admin
select is((select count(*)::int from public.school_families where id = :'fam_a'), 1, 'сім''я A лишається: там є другі батьки');
select is((select count(*)::int from auth.users where id = :'p1'), 0, 'акаунт p1 видалено');
select is((select count(*)::int from public.school_members where family_id = :'fam_a'), 2, 'у сім''ї A лишились q і c1');
:as_p2
select lives_ok($$ select public.school_delete_account() $$, 'p2 (єдині батьки) видаляє акаунт');
:as_admin
select is((select count(*)::int from public.school_families where id = :'fam_b'), 0, 'сім''я B видалена разом з єдиними батьками');
select is((select count(*)::int from auth.users where id = :'c2'), 1, 'акаунт дитини c2 лишається (лише без сім''ї)');
select is((select count(*)::int from public.school_families where id = :'fam_a'), 1, 'чужа сім''я A не зачеплена');

select * from finish(true);
rollback;
