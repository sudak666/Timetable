import webpush from "npm:web-push@3.6.7";
import { createClient } from "npm:@supabase/supabase-js@2";

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
const uah = (v: number) => (v > 0 ? "+" : "") + v + " ₴";

async function send(userIds: string[], title: string, body: string, tag: string) {
  if (!userIds.length) return;
  const { data } = await sb.from("school_push").select("endpoint,sub").in("user_id", userIds);
  await Promise.all((data ?? []).map(async (s) => {
    try {
      await webpush.sendNotification(s.sub, JSON.stringify({ title, body, tag }), { TTL: 86400 });
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

Deno.serve(async (req) => {
  const c = await secrets();
  if (req.headers.get("x-hook-secret") !== c.hook_secret) return new Response("forbidden", { status: 403 });
  const { table, type, record: r, old_record: o } = await req.json();
  if (table === "school_grades" && type === "INSERT" && r.status === "pending") {
    await send(await parents(r.family_id), `📝 ${await name(r.family_id, r.child_id)}: ${r.subject} — ${r.grade}`, "Нова оцінка чекає підтвердження ✓", "g" + r.id);
  } else if (table === "school_grades" && type === "UPDATE" && o?.status === "pending" && r.status === "approved") {
    await send([r.child_id], `✅ ${r.subject}: ${r.grade} підтверджено`, `Нараховано ${uah(r.amount ?? 0)}`, "g" + r.id);
  } else if (table === "school_ledger" && type === "INSERT") {
    await send([r.child_id], r.kind === "payout" ? `💸 Виплачено ${r.amount} ₴` : r.amount >= 0 ? `🎁 Бонус ${uah(r.amount)}` : `⚠️ Штраф ${r.amount} ₴`, r.note || "", "l" + r.id);
  } else if (table === "school_challenges" && type === "INSERT") {
    await send([r.child_id], `🏅 Новий челендж: +${r.reward} ₴`, r.title, "c" + r.id);
  }
  return new Response("ok");
});
