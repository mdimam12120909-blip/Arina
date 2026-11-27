import { createClient } from "jsr:@supabase/supabase-js@2";

// Every AI key stays here, on the server. Phones send a request and get an answer; they never see a key.
const NAMES = ["gemini1","gemini2","gemini3","gemini4","gemini5","openai","anthropic","groq","deepseek","cartesia","fish"];
const J = { "Content-Type": "application/json" };
const out = (o: unknown, status = 200) => new Response(JSON.stringify(o), { status, headers: J });
const zulu = (ms: number) => new Date(Date.now() + ms).toISOString().replace(/\.\d{3}Z$/, "Z");

const BRAINS: Record<string, { url: string; auth: (k: string) => Record<string, string> }> = {
  openai: { url: "https://api.openai.com/v1/chat/completions", auth: (k) => ({ Authorization: `Bearer ${k}` }) },
  groq: { url: "https://api.groq.com/openai/v1/chat/completions", auth: (k) => ({ Authorization: `Bearer ${k}` }) },
  deepseek: { url: "https://api.deepseek.com/chat/completions", auth: (k) => ({ Authorization: `Bearer ${k}` }) },
  anthropic: { url: "https://api.anthropic.com/v1/messages", auth: (k) => ({ "x-api-key": k, "anthropic-version": "2023-06-01" }) },
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return out({ error: "post only" }, 405);
  const token = (req.headers.get("Authorization") ?? "").replace("Bearer ", "");
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: u, error } = await admin.auth.getUser(token);
  if (error || !u?.user) return out({ error: "unauthorized" }, 401);
  const until = (u.user as any).banned_until;
  if (until && new Date(until) > new Date()) return out({ error: "banned" }, 403);

  let r: any;
  try { r = await req.json(); } catch { return out({ error: "bad request" }, 400); }
  const { data: rows, error: e2 } = await admin.from("app_secrets").select("key,value").in("key", NAMES);
  if (e2) return out({ error: "server" }, 500);
  const keys: Record<string, string> = {};
  for (const x of rows ?? []) if (String(x.value).trim()) keys[x.key] = String(x.value).trim();

  // Which brains exist (names only, never keys).
  if (r.action === "status") return out({ providers: Object.keys(keys).filter((k) => !k.startsWith("gemini")), gemini: Object.keys(keys).filter((k) => k.startsWith("gemini")).length });

  const slot = Math.max(0, Math.min(4, Number(r.slot ?? 0)));
  const gemKey = keys[`gemini${slot + 1}`] ?? keys.gemini1 ?? Object.entries(keys).find(([k]) => k.startsWith("gemini"))?.[1];

  if (r.action === "gen") {
    if (!gemKey) return out({ error: "no key" }, 503);
    const model = String(r.model ?? "");
    if (!/^[a-z0-9.\-]+$/.test(model)) return out({ error: "bad model" }, 400);
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${model}:${r.stream ? "streamGenerateContent?alt=sse" : "generateContent"}`;
    const up = await fetch(url, { method: "POST", headers: { ...J, "x-goog-api-key": gemKey }, body: JSON.stringify(r.body) });
    return new Response(up.body, { status: up.status, headers: { "Content-Type": up.headers.get("Content-Type") ?? "application/json" } });
  }

  if (r.action === "live_token") {
    if (!gemKey) return out({ error: "no key" }, 503);
    const up = await fetch("https://generativelanguage.googleapis.com/v1beta/auth_tokens", {
      method: "POST", headers: { ...J, "x-goog-api-key": gemKey },
      body: JSON.stringify({ uses: 1, expireTime: zulu(30 * 60_000), newSessionExpireTime: zulu(60_000) }),
    });
    const t = await up.text();
    if (!up.ok) { console.log("auth_tokens failed", up.status, t.slice(0, 300)); return out({ error: "token", status: up.status, detail: t.slice(0, 200) }, 502); }
    return out({ token: JSON.parse(t).name });
  }

  if (r.action === "tts" && String(r.provider ?? "cartesia") === "fish") {
    const fk = keys.fish;
    if (!fk) return out({ error: "no key" }, 503);
    const text = String(r.text ?? "").slice(0, 600);
    const voice = String(r.voice ?? "");
    const model = String(r.model ?? "s2.1-pro");
    if (!text.trim() || !/^[A-Za-z0-9\-]{8,64}$/.test(voice) || !/^[a-z0-9.\-]+$/.test(model)) return out({ error: "bad request" }, 400);
    const up = await fetch("https://api.fish.audio/v1/tts", {
      method: "POST",
      headers: { ...J, Authorization: `Bearer ${fk}`, model },
      body: JSON.stringify({ text, reference_id: voice, format: "pcm", sample_rate: 24000, latency: "low", normalize: true }),
    });
    if (!up.ok) { const t = await up.text(); console.log("fish failed", up.status, t.slice(0, 300)); return out({ error: "tts", status: up.status, detail: t.slice(0, 200) }, 502); }
    return new Response(up.body, { status: 200, headers: { "Content-Type": "application/octet-stream" } });
  }

  if (r.action === "tts") {
    const ck = keys.cartesia;
    if (!ck) return out({ error: "no key" }, 503);
    const text = String(r.text ?? "").slice(0, 600);
    const voice = String(r.voice ?? "");
    const model = String(r.model ?? "sonic-3");
    const lang = String(r.lang ?? "en");
    if (!text.trim() || !/^[A-Za-z0-9\-]{8,64}$/.test(voice) || !/^[a-z0-9.\-]+$/.test(model) || !/^[a-z]{2,3}$/.test(lang)) return out({ error: "bad request" }, 400);
    const up = await fetch("https://api.cartesia.ai/tts/bytes", {
      method: "POST",
      headers: { ...J, "X-API-Key": ck, "Cartesia-Version": "2025-04-16" },
      body: JSON.stringify({ model_id: model, transcript: text, voice: { mode: "id", id: voice }, language: lang, output_format: { container: "raw", encoding: "pcm_s16le", sample_rate: 24000 } }),
    });
    if (!up.ok) { const t = await up.text(); console.log("cartesia failed", up.status, t.slice(0, 300)); return out({ error: "tts", status: up.status, detail: t.slice(0, 200) }, 502); }
    return new Response(up.body, { status: 200, headers: { "Content-Type": "application/octet-stream" } });
  }

  if (r.action === "brain") {
    const b = BRAINS[String(r.provider)];
    const k = keys[String(r.provider)];
    if (!b || !k) return out({ error: "no key" }, 503);
    const up = await fetch(b.url, { method: "POST", headers: { ...J, ...b.auth(k) }, body: JSON.stringify(r.body) });
    return new Response(up.body, { status: up.status, headers: { "Content-Type": up.headers.get("Content-Type") ?? "application/json" } });
  }

  return out({ error: "unknown action" }, 400);
});
