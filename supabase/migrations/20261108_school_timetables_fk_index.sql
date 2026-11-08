-- Радник Supabase: FK (family_id, child_id) → school_members без покривного індексу.
-- Складений індекс покриває і FK, і фільтр «розклади сім'ї» (family_id = …), тож старий не потрібен.
create index school_timetables_family_child_idx on public.school_timetables (family_id, child_id);
drop index public.school_timetables_family_idx;
