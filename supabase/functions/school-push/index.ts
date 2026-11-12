import webpush from "npm:web-push@3.6.7";
import { createClient } from "npm:@supabase/supabase-js@2";
import { FcmAuth, fcmAlertMessage, parseServiceAccount, sendFcm, sendFcmMessage, type Push } from "./fcm.ts";
import { ALERTS_URL, alertText, parseStatuses, transitions, type AlertEvent, type AlertLocation, type AlertState } from "./alerts.ts";

const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
let cfg: Record<string, string> | null = null;
async function secrets() {
  if (!cfg) {
    const { data } = await sb.from("school_secrets").select("k,v");
    cfg = Object.fromEntries((data ?? []).map((r) => [r.k, r.v]));
    webpush.setVapidDetails("mailto:noreply@timetable.app", cfg.vapid_public, cfg.vapid_private);
  }
  return cfg;
}
// Android (Firebase). Секрет FCM_SERVICE_ACCOUNT — JSON сервісного акаунта; без нього Android просто пропускаємо.
const fcmAccount = parseServiceAccount(Deno.env.get("FCM_SERVICE_ACCOUNT"));
const fcmAuth = fcmAccount ? new FcmAuth(fcmAccount) : null;

async function sendAndroid(userIds: string[], p: Push) {
  if (!fcmAccount || !fcmAuth) return;
  const { data } = await sb.from("school_fcm_tokens").select("token").in("user_id", userIds);
  await Promise.all((data ?? []).map(async ({ token }) => {
    const res = await sendFcm(fcmAuth, fcmAccount.project_id, token, p);
    if (res === "unregistered") await sb.from("school_fcm_tokens").delete().eq("token", token);
  }));
}

const uah = (v: number) => (v > 0 ? "+" : "") + v + " ₴";

async function send(userIds: string[], title: string, body: string, tag: string, url = "#grades", channel: Push["channel"] = "family") {
  if (!userIds.length) return;
  // Збій Firebase не повинен зупинити web-push (і навпаки).
  try {
    await sendAndroid(userIds, { title, body, tag, url, channel });
  } catch (e) {
    console.error("fcm", e);
  }
  const { data } = await sb.from("school_push").select("endpoint,sub").in("user_id", userIds);
  await Promise.all((data ?? []).map(async (s) => {
    try {
      await webpush.sendNotification(s.sub, JSON.stringify({ title, body, tag, url }), { TTL: 86400 });
    } catch (e) {
      const code = (e as { statusCode?: number }).statusCode;
      if (code === 404 || code === 410) await sb.from("school_push").delete().eq("endpoint", s.endpoint);
    }
  }));
}
async function parents(fid: string) {
  const { data } = await sb.from("school_members").select("user_id").eq("family_id", fid).eq("role", "parent");
  return (data ?? []).map((r) => r.user_id);
}
async function name(fid: string, uid: string) {
  const { data } = await sb.from("school_members").select("name").eq("family_id", fid).eq("user_id", uid).maybeSingle();
  return data?.name || "Дитина";
}

/** Щовечора: дитині — невиконана домашка на завтра (дата за Києвом). */
async function remindHomework() {
  const kyiv = (d: Date) => new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Kyiv" }).format(d);
  const tomorrow = kyiv(new Date(Date.now() + 864e5));
  const { data } = await sb.from("school_homework").select("child_id,subject").eq("due", tomorrow).eq("done", false);
  const by = new Map<string, string[]>();
  for (const h of data ?? []) by.set(h.child_id, [...(by.get(h.child_id) ?? []), h.subject]);
  await Promise.all([...by].map(([kid, subj]) =>
    send([kid], `📝 На завтра ${subj.length} ${subj.length < 5 ? "завдання" : "завдань"}`, [...new Set(subj)].join(", "), "hw" + tomorrow, "#hw", "homework")));
}

/**
 * Щохвилини (pg_cron → school_alerts_tick): стан тривог для місць, які обрали сім'ї.
 * Токен alerts.in.ua — секрет ALERTS_IN_UA_TOKEN; без нього нічого не робимо.
 */
async function checkAlerts() {
  const token = Deno.env.get("ALERTS_IN_UA_TOKEN");
  if (!token) return;
  const { data: fam } = await sb.from("school_families").select("alert_uid").not("alert_uid", "is", null);
  const uids = [...new Set((fam ?? []).map((f) => f.alert_uid as number))];
  if (!uids.length) return;

  const res = await fetch(ALERTS_URL, { headers: { Authorization: `Bearer ${token}` }, signal: AbortSignal.timeout(15_000) });
  if (!res.ok) {
    console.warn("alerts.in.ua", res.status);
    return;
  }
  const statuses = parseStatuses(await res.json(), Math.max(...uids));
  if (!statuses) {
    console.warn("alerts.in.ua: підозріла відповідь, пропускаємо");
    return;
  }

  const [{ data: locs }, { data: prev }] = await Promise.all([
    sb.from("school_alert_locations").select("uid,name,kind,oblast_uid,raion_uid,separate").in("uid", uids),
    sb.from("school_alert_state").select("uid,status,since,checked_at").in("uid", uids),
  ]);
  const { states, events } = transitions((locs ?? []) as AlertLocation[], statuses, (prev ?? []) as AlertState[], new Date());
  const { error } = await sb.from("school_alert_state").upsert(states);
  if (error) {
    // Не зберегли стан — не сповіщаємо, інакше наступної хвилини подія повториться.
    console.error("alert_state", error.message);
    return;
  }
  const statusOf = new Map(states.map((s) => [s.uid, s.status]));
  await Promise.all(events.map((e) => notifyAlert(e, statusOf.get(e.uid) ?? "N")));
}

/** Push про тривогу — лише на пристрої, де її ввімкнули (school_fcm_tokens.alerts), у сім'ях цього місця. */
async function notifyAlert(e: AlertEvent, status: string) {
  if (!fcmAccount || !fcmAuth) return;
  const { data: fams } = await sb.from("school_families").select("id").eq("alert_uid", e.uid);
  const familyIds = (fams ?? []).map((f) => f.id);
  if (!familyIds.length) return;
  const { data: members } = await sb.from("school_members").select("user_id").in("family_id", familyIds);
  const userIds = [...new Set((members ?? []).map((m) => m.user_id))];
  if (!userIds.length) return;
  const { data: tokens } = await sb.from("school_fcm_tokens").select("token").in("user_id", userIds).eq("alerts", true);
  const { title, body } = alertText(e);
  await Promise.all((tokens ?? []).map(async ({ token }) => {
    const msg = fcmAlertMessage(token, { uid: e.uid, status, since: e.since, title, body });
    const res = await sendFcmMessage(fcmAuth, fcmAccount.project_id, msg);
    if (res === "unregistered") await sb.from("school_fcm_tokens").delete().eq("token", token);
  }));
}

Deno.serve(async (req) => {
  const c = await secrets();
  if (req.headers.get("x-hook-secret") !== c.hook_secret) return new Response("forbidden", { status: 403 });
  const { table, type, record: r, old_record: o } = await req.json();
  if (type === "CRON" && table === "hw_remind") {
    await remindHomework();
    return new Response("ok");
  }
  if (type === "CRON" && table === "alerts") {
    try {
      await checkAlerts();
    } catch (e) {
      console.error("alerts", e);
    }
    return new Response("ok");
  }
  if (table === "school_grades" && type === "INSERT" && r.status === "pending") {
    await send(await parents(r.family_id), `📝 ${await name(r.family_id, r.child_id)}: ${r.subject} — ${r.grade === 0 ? "Зар." : r.grade}`, "Нова оцінка чекає підтвердження ✓", "g" + r.id, `#grades?kid=${r.child_id}`);
  } else if (table === "school_grades" && type === "UPDATE" && o?.status === "pending" && r.status === "approved") {
    await send([r.child_id], `✅ ${r.subject}: ${r.grade === 0 ? "Зар." : r.grade} підтверджено`, `Нараховано ${uah(r.amount ?? 0)}`, "g" + r.id);
  } else if (table === "school_ledger" && type === "INSERT") {
    await send([r.child_id], r.kind === "payout" ? `💸 Виплачено ${r.amount} ₴` : r.amount >= 0 ? `🎁 Бонус ${uah(r.amount)}` : `⚠️ Штраф ${r.amount} ₴`, r.note || "", "l" + r.id);
  } else if (table === "school_challenges" && type === "INSERT") {
    await send([r.child_id], `🏅 Новий челендж: +${r.reward} ₴`, r.title, "c" + r.id);
  } else if (table === "school_members" && type === "INSERT") {
    // Хтось увійшов за кодом — батьки мають знати (захист від підібраного чи злитого коду).
    // Того, хто створив сім'ю, не сповіщаємо: інших батьків ще немає.
    const to = (await parents(r.family_id)).filter((id) => id !== r.user_id);
    const who = r.name || (r.role === "parent" ? "Хтось" : "Дитина");
    await send(to, `👋 ${who} — новий учасник сім'ї`, r.role === "parent"
      ? "Приєднався за кодом для батьків. Не знаєте цю людину? Приберіть її в профілі й змініть код."
      : "Приєднався за кодом для дитини. Не знаєте цю людину? Приберіть її в профілі й змініть код.", "m" + r.user_id, "#me");
  }
  return new Response("ok");
});
