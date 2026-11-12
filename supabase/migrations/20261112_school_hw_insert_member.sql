-- B9 з аудиту 12.11.2026 (docs/audit-2026-11-12.md): домашку можна додати лише дитині цієї сім'ї.
--
-- Було: батьки могли вставити домашку з будь-яким child_id (оцінки й розклад це вже перевіряли — gr_ins, tt_ins).
-- Стало: як у tt_ins — child_id має бути дитиною цієї сім'ї; додає або хтось із батьків, або сама дитина собі.
-- Наявні рядки не змінюються (політика діє лише на нові вставки).

drop policy if exists hw_ins on public.school_homework;
create policy hw_ins on public.school_homework
  for insert to authenticated
  with check (
    exists (
      select 1 from public.school_members m
      where m.family_id = school_homework.family_id
        and m.user_id = school_homework.child_id
        and m.role = 'child'
    )
    and (public.school_role(family_id) = 'parent' or child_id = (select auth.uid()))
  );
