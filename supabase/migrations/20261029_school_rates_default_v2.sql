-- Дефолтний курс нових сімей = DEFAULT_RATES у src (6 → 0, мінуси для 1–5).
alter table public.school_families alter column rates set default '{"1":-300,"2":-200,"3":-150,"4":-100,"5":-70,"6":0,"7":70,"8":100,"9":150,"10":200,"11":250,"12":300}'::jsonb;
