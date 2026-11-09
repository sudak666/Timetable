"""
Генерує міграцію з довідником місць alerts.in.ua (області → райони → громади).

Джерело — офіційна таблиця UID від alerts.in.ua (посилання на devs.alerts.in.ua):
  https://docs.google.com/spreadsheets/d/1XnTOzcPHd1LZUrarR1Fk43FUyl8Ae6a6M7pcwDRjNdA/export?format=csv

Використання:
  python -I supabase/scripts/alert_locations.py locations.csv > supabase/migrations/<дата>_school_alert_locations.sql

Таблиця ієрархічна лише за порядком рядків: область, далі її райони, під кожним районом — громади.
Пастка: рядок «Автономна Республіка Крим» стоїть між Волинською областю та її районами,
тож Крим, Севастополь і Київ (без районів у таблиці) не стають «поточною» областю.
"""

import csv
import sys

TOP_KINDS = {"Область": "oblast", "Місто з спеціальним статусом": "city"}
NO_CHILDREN = {29, 30, 31}  # АР Крим, м. Севастополь, м. Київ
SEPARATE_NOTE = "оголошуються окремо"


def parse(path):
    rows = list(csv.reader(open(path, encoding="utf-8")))
    header = next(i for i, r in enumerate(rows) if r[:3] == ["UID", "Назва", "Тип"])
    out, oblast, raion = [], None, None
    for r in rows[header + 1 :]:
        uid, name, kind, note = int(r[0]), r[1].strip(), r[2].strip(), r[3].strip()
        separate = SEPARATE_NOTE in note
        if kind in TOP_KINDS:
            out.append((uid, name, TOP_KINDS[kind], None, None, separate or uid == 31))
            if uid not in NO_CHILDREN:
                oblast, raion = uid, None
        elif kind == "Район":
            assert oblast is not None, r
            raion = uid
            out.append((uid, name, "raion", oblast, None, separate))
        elif kind == "Громада":
            assert oblast is not None and raion is not None, r
            out.append((uid, name, "hromada", oblast, raion, separate))
        else:
            raise ValueError(f"Невідомий тип: {r}")
    return out


def check(locs):
    by = {l[0]: l for l in locs}
    assert len(by) == len(locs), "UID повторюються"
    kinds = [l[2] for l in locs]
    assert kinds.count("oblast") == 25 and kinds.count("city") == 2, kinds
    # Відомі зв'язки — ловлять зсув ієрархії (див. пастку з Кримом).
    assert by[38][3] == 8, "Володимирський район має бути у Волинській області"
    assert by[124][3] == 22, "Харківський район — у Харківській області"
    assert by[1293][3:5] == (22, 124) and by[1293][5], "м. Харків — у Харківському районі, тривоги окремо"
    assert by[564][5], "м. Запоріжжя — тривоги окремо"
    assert not any(l[3] == 29 for l in locs), "у Криму в таблиці немає районів"
    for l in locs:
        if l[2] == "hromada":
            assert by[l[4]][3] == l[3], f"район громади {l} з іншої області"


def sql(locs):
    q = lambda s: "'" + s.replace("'", "''") + "'"
    n = lambda v: "null" if v is None else str(v)
    lines = [
        "-- Довідник місць alerts.in.ua: області, райони, громади (UID = позиція в рядку статусів API).",
        "-- Згенеровано supabase/scripts/alert_locations.py — не редагувати вручну.",
        "insert into public.school_alert_locations (uid, name, kind, oblast_uid, raion_uid, separate) values",
    ]
    # Спершу області, потім райони, потім громади — щоб посилання на батьків уже існували.
    order = {"oblast": 0, "city": 0, "raion": 1, "hromada": 2}
    vals = [
        f"  ({l[0]}, {q(l[1])}, '{l[2]}', {n(l[3])}, {n(l[4])}, {'true' if l[5] else 'false'})"
        for l in sorted(locs, key=lambda l: (order[l[2]], l[0]))
    ]
    lines.append(",\n".join(vals))
    lines.append("on conflict (uid) do update set name = excluded.name, kind = excluded.kind, oblast_uid = excluded.oblast_uid,")
    lines.append("  raion_uid = excluded.raion_uid, separate = excluded.separate;")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    locs = parse(sys.argv[1])
    check(locs)
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    sys.stdout.write(sql(locs))
    print(f"{len(locs)} місць", file=sys.stderr)
