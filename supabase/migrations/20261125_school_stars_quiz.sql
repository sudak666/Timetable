-- Зірки за поведінку й гроші за вікторину (25.11.2026, побажання дитини).
--
-- Зірки: ставлять лише батьки — це звичайний бонус у гаманці (kind = 'bonus'), позначений src = 'star'.
--   Тому старі версії застосунку й сайту рахують ці гроші правильно, просто без зірочок.
-- Вікторина: питання й правильні відповіді — лише на сервері. Відповідь перевіряє school_quiz_answer:
--   платимо лише за ПЕРШУ відповідь на питання за день і не більше 3 правильних на день.
--   Ціни — у school_families.rates: star_price (за замовчуванням 50), quiz_price (20).

-- ───────── гаманець: звідки запис ─────────
alter table public.school_ledger add column if not exists src text;
alter table public.school_ledger drop constraint if exists school_ledger_src_check;
alter table public.school_ledger add constraint school_ledger_src_check check (src is null or src in ('star', 'quiz'));

-- Батьки нараховують і зірки; гроші за вікторину — лише через school_quiz_answer.
drop policy if exists lg_ins on public.school_ledger;
create policy lg_ins on public.school_ledger as permissive for insert to authenticated
  with check (
    public.school_role(family_id) = 'parent'
    and (src is null or src = 'star')
    and exists (select 1 from public.school_members m
                where m.family_id = school_ledger.family_id and m.user_id = school_ledger.child_id and m.role = 'child')
  );

-- ───────── банк питань (перша відповідь — правильна) ─────────
create table if not exists public.school_quiz (
  id smallint primary key,
  q text not null check (length(q) <= 200),
  answers text[] not null check (cardinality(answers) between 2 and 6)
);
alter table public.school_quiz enable row level security;
-- Ні політик, ні прав: правильні відповіді не віддаємо нікому, крім функцій нижче.
revoke all on public.school_quiz from anon, authenticated;

insert into public.school_quiz (id, q, answers) values
  (1, 'Скільки буде 7 × 8?', array['56', '54', '64', '48']),
  (2, 'Столиця Австралії?', array['Канберра', 'Сідней', 'Мельбурн', 'Перт']),
  (3, 'Хто написав «Кобзар»?', array['Тарас Шевченко', 'Іван Франко', 'Леся Українка', 'Григорій Сковорода']),
  (4, 'Яка планета найбільша?', array['Юпітер', 'Сатурн', 'Земля', 'Нептун']),
  (5, 'H₂O — це…', array['вода', 'сіль', 'кисень', 'водень']),
  (6, 'Past Simple від «go»?', array['went', 'goed', 'gone', 'goes']),
  (7, 'Скільки градусів у прямому куті?', array['90', '180', '45', '100']),
  (8, 'Найдовша річка України?', array['Дніпро', 'Дністер', 'Південний Буг', 'Десна']),
  (9, 'Скільки сторін у шестикутника?', array['6', '5', '8', '7']),
  (10, 'Хто автор «Лісової пісні»?', array['Леся Українка', 'Ольга Кобилянська', 'Марко Вовчок', 'Ліна Костенко']),
  (11, 'Що таке «apple»?', array['яблуко', 'груша', 'слива', 'апельсин']),
  (12, '√81 = ?', array['9', '8', '7', '18']),
  (13, 'Найвища гора України?', array['Говерла', 'Піп Іван', 'Петрос', 'Бребенескул']),
  (14, 'Який газ ми видихаємо?', array['вуглекислий', 'кисень', 'азот', 'гелій']),
  (15, 'Скільки хвилин у 2,5 годинах?', array['150', '120', '250', '130']),
  (16, 'У якому році Україна проголосила незалежність?', array['1991', '1990', '1996', '2001']),
  (17, 'Множина від «child»?', array['children', 'childs', 'childes', 'childrens']),
  (18, 'Що більше: 0,5 чи 0,45?', array['0,5', '0,45', 'однакові', 'не можна порівняти']),
  (19, 'Хто намалював «Мону Лізу»?', array['Леонардо да Вінчі', 'Мікеланджело', 'Рафаель', 'Пікассо']),
  (20, 'Яка частина мови «швидко»?', array['прислівник', 'прикметник', 'дієслово', 'іменник']),
  (21, 'Скільки материків на Землі?', array['6', '5', '7', '4']),
  (22, 'Що вимірюють у ватах?', array['потужність', 'масу', 'довжину', 'час']),
  (23, 'Перший президент України?', array['Леонід Кравчук', 'Леонід Кучма', 'Віктор Ющенко', 'Михайло Грушевський']),
  (24, '1 кілобайт ≈ скільки байтів?', array['1024', '1000000', '100', '512']),
  (25, 'Як англійською «вівторок»?', array['Tuesday', 'Thursday', 'Monday', 'Wednesday']),
  (26, 'Площа квадрата зі стороною 5?', array['25', '20', '10', '15']),
  (27, 'Яке море омиває Україну на півдні?', array['Чорне', 'Балтійське', 'Середземне', 'Червоне']),
  (28, 'Найшвидша наземна тварина?', array['гепард', 'лев', 'антилопа', 'кінь']),
  (29, 'Скільки кісток у дорослої людини?', array['206', '306', '106', '256']),
  (30, 'Хто написав «Захар Беркут»?', array['Іван Франко', 'Панас Мирний', 'Іван Нечуй-Левицький', 'Михайло Коцюбинський'])
on conflict (id) do update set q = excluded.q, answers = excluded.answers;

-- ───────── хто на що вже відповідав сьогодні (день — за Києвом) ─────────
create table if not exists public.school_quiz_answers (
  user_id uuid not null references auth.users(id) on delete cascade,
  day date not null,
  quiz_id smallint not null references public.school_quiz(id) on delete cascade,
  correct boolean not null,
  paid integer not null default 0,
  created_at timestamptz not null default now(),
  primary key (user_id, day, quiz_id)
);
create index if not exists school_quiz_answers_quiz_id_idx on public.school_quiz_answers (quiz_id);
alter table public.school_quiz_answers enable row level security;
revoke all on public.school_quiz_answers from anon, authenticated;

-- ───────── раунд: n питань, на які сьогодні ще не відповідали; варіанти перемішані ─────────
create or replace function public.school_quiz_round(n integer default 3)
returns table (id smallint, q text, options text[])
language plpgsql
security definer
set search_path to ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_day date := (now() at time zone 'Europe/Kyiv')::date;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  return query
    select z.id, z.q, array(select a from unnest(z.answers) as a order by random())
    from public.school_quiz z
    order by (exists (select 1 from public.school_quiz_answers s
                      where s.user_id = v_uid and s.day = v_day and s.quiz_id = z.id)), random()
    limit least(greatest(coalesce(n, 3), 1), 10);
end
$function$;

-- ───────── відповідь: перевіряє сервер, платить дитині за першу правильну, до 3 на день ─────────
create or replace function public.school_quiz_answer(p_id smallint, p_answer text)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
  v_uid uuid := auth.uid();
  v_day date := (now() at time zone 'Europe/Kyiv')::date;
  v_q public.school_quiz;
  v_ok boolean;
  v_first boolean;
  v_fam uuid;
  v_raw text;
  v_price integer := 0;
  v_count integer := 0;
  v_paid integer := 0;
  c_per_day constant integer := 3;
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  select * into v_q from public.school_quiz where id = p_id;
  if not found then
    raise exception 'unknown question' using errcode = '22023';
  end if;
  v_ok := coalesce(p_answer = v_q.answers[1], false);

  -- Два одночасні запити однієї людини не повинні обійти денний ліміт.
  perform pg_advisory_xact_lock(hashtextextended('school_quiz:' || v_uid::text, 0));

  -- Потрібні лише сьогоднішні записи (ліміт — на день); вчорашні стираємо одразу.
  delete from public.school_quiz_answers where user_id = v_uid and day < v_day;

  insert into public.school_quiz_answers (user_id, day, quiz_id, correct)
    values (v_uid, v_day, p_id, v_ok)
    on conflict do nothing;
  v_first := found;

  -- Гроші — лише дитині сім'ї (батьки грають просто так).
  select m.family_id into v_fam from public.school_members m
    where m.user_id = v_uid and m.role = 'child' order by m.family_id limit 1;

  if v_fam is not null then
    select count(*) into v_count from public.school_quiz_answers s
      where s.user_id = v_uid and s.day = v_day and s.paid > 0;
    if v_first and v_ok and v_count < c_per_day then
      select f.rates ->> 'quiz_price' into v_raw from public.school_families f where f.id = v_fam;
      v_price := case when v_raw ~ '^-?[0-9]{1,6}$' then least(greatest(v_raw::integer, 0), 1000) else 20 end;
      if v_price > 0 then
        insert into public.school_ledger (family_id, child_id, kind, amount, note, date, src)
          values (v_fam, v_uid, 'bonus', v_price, left('🧠 Вікторина: ' || v_q.q, 200), v_day, 'quiz');
        update public.school_quiz_answers set paid = v_price
          where user_id = v_uid and day = v_day and quiz_id = p_id;
        v_paid := v_price;
        v_count := v_count + 1;
      end if;
    end if;
  end if;

  return jsonb_build_object(
    'correct', v_ok,
    'right', v_q.answers[1],
    'paid', v_paid,
    'first', v_first,
    'left', case when v_fam is null then 0 else greatest(c_per_day - v_count, 0) end
  );
end
$function$;

revoke all on function public.school_quiz_round(integer) from public, anon;
revoke all on function public.school_quiz_answer(smallint, text) from public, anon;
grant execute on function public.school_quiz_round(integer) to authenticated;
grant execute on function public.school_quiz_answer(smallint, text) to authenticated;
