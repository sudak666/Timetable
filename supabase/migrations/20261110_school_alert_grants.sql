-- school_families має права по стовпцях (parent_code навмисно прихований від SELECT),
-- тож новий стовпець alert_uid треба відкрити явно: читати — усім членам сім'ї (RLS fam_sel),
-- змінювати — лише батькам (RLS fam_upd).
grant select (alert_uid) on public.school_families to authenticated;
grant update (alert_uid) on public.school_families to authenticated;
