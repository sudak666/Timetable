-- Батьки бачать, як дитина сьогодні відповідала у вікторині (26.11.2026).
--
-- • school_quiz_answers.answer — що саме обрала дитина (лише сьогодні: наступного дня записи стираються, як і раніше).
-- • school_quiz_today(p_child) — сьогоднішні відповіді дитини для її батьків: питання, відповідь, правильна, гроші.
-- • Після кожної відповіді — тригер на edge-функцію school-push: вона сповіщає батьків, коли дитина
--   відповіла на 3, 6, 9… питань за день (раз на раунд, не після кожного натискання).
-- • school_quiz_answer тепер рахує гроші ДО запису відповіді, тож рядок (і сповіщення) одразу має paid.

alter table public.school_quiz_answers add column if not exists answer text;
alter table public.school_quiz_answers drop constraint if exists school_quiz_answers_answer_check;
alter table public.school_quiz_answers add constraint school_quiz_answers_answer_check check (answer is null or length(answer) <= 200);

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

  -- Два одночасні запити однієї людини не повинні обійти денний ліміт (і перевірку «перша відповідь»).
  perform pg_advisory_xact_lock(hashtextextended('school_quiz:' || v_uid::text, 0));

  -- Потрібні лише сьогоднішні записи (ліміт — на день); вчорашні стираємо одразу.
  delete from public.school_quiz_answers where user_id = v_uid and day < v_day;

  v_first := not exists (select 1 from public.school_quiz_answers s
                         where s.user_id = v_uid and s.day = v_day and s.quiz_id = p_id);

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
        v_paid := v_price;
        v_count := v_count + 1;
      end if;
    end if;
  end if;

  if v_first then
    insert into public.school_quiz_answers (user_id, day, quiz_id, correct, paid, answer)
      values (v_uid, v_day, p_id, v_ok, v_paid, left(p_answer, 200));
  end if;
  if v_paid > 0 then
    insert into public.school_ledger (family_id, child_id, kind, amount, note, date, src)
      values (v_fam, v_uid, 'bonus', v_paid, left('🧠 Вікторина: ' || v_q.q, 200), v_day, 'quiz');
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

-- ───────── сьогоднішні відповіді дитини — лише для батьків її сім'ї ─────────
create or replace function public.school_quiz_today(p_child uuid)
returns table (quiz_id smallint, q text, answer text, right_answer text, correct boolean, paid integer, answered_at timestamptz)
language plpgsql
stable
security definer
set search_path to ''
as $function$
declare
  v_uid uuid := auth.uid();
begin
  if v_uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  if not exists (
    select 1 from public.school_members p
    join public.school_members c on c.family_id = p.family_id
    where p.user_id = v_uid and p.role = 'parent' and c.user_id = p_child and c.role = 'child'
  ) then
    raise exception 'forbidden' using errcode = '42501';
  end if;
  return query
    select s.quiz_id, z.q, s.answer, z.answers[1], s.correct, s.paid, s.created_at
    from public.school_quiz_answers s
    join public.school_quiz z on z.id = s.quiz_id
    where s.user_id = p_child and s.day = (now() at time zone 'Europe/Kyiv')::date
    order by s.created_at, s.quiz_id;
end
$function$;

revoke all on function public.school_quiz_today(uuid) from public, anon;
grant execute on function public.school_quiz_today(uuid) to authenticated;

-- ───────── сповіщення батькам (обробник — edge-функція school-push) ─────────
drop trigger if exists school_quiz_answers_push on public.school_quiz_answers;
create trigger school_quiz_answers_push
  after insert on public.school_quiz_answers
  for each row execute function public.school_push_hook();
