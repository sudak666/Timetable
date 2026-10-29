-- Індекси під FK (lint 0001) і pg_net поза public (lint 0014).
create index if not exists school_challenges_child_id_idx on public.school_challenges(child_id);
create index if not exists school_grades_child_id_idx on public.school_grades(child_id);
create index if not exists school_homework_child_id_idx on public.school_homework(child_id);
create index if not exists school_ledger_child_id_idx on public.school_ledger(child_id);
create index if not exists school_push_user_id_idx on public.school_push(user_id);
create index if not exists completed_work_created_by_idx on public.completed_work(created_by);
create index if not exists inventory_audit_items_audit_id_idx on public.inventory_audit_items(audit_id);
create index if not exists inventory_logs_item_id_idx on public.inventory_logs(item_id);
create index if not exists inventory_receipts_item_id_idx on public.inventory_receipts(item_id);
drop extension if exists pg_net;
create extension pg_net schema extensions;
