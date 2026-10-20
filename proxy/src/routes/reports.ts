import type { Env } from "../env.ts";
import { logRequest } from "../logging.ts";
import { preChecks } from "../pipeline.ts";
import { reportRequestSchema } from "../schemas/path.ts";

const NINETY_DAYS_MS = 90 * 24 * 60 * 60 * 1000;

/**
 * POST /v1/reports — "Report this quest" (ARCH §3.7/§3.8). The lightest route: size/schema check, Integrity (or
 * staging debug key) and the per-IP backstop from `preChecks`, then an insert. No model call, no per-install
 * rate-limit counters (not specified for this route), and — the point of the route — **no install ID stored**.
 * `scheduled()` in src/index.ts sweeps rows past `expiresAtMs`.
 */
export async function handleReport(request: Request, env: Env): Promise<Response> {
  const started = Date.now();
  const pre = await preChecks(request, env, reportRequestSchema);
  if (!pre.ok) return pre.response;
  const { body } = pre.value;

  const now = Date.now();
  await env.REPORTS_DB.prepare(
    "INSERT INTO reports (id, promptVersion, reason, title, description, createdAtMs, expiresAtMs) VALUES (?, ?, ?, ?, ?, ?, ?)",
  )
    .bind(crypto.randomUUID(), body.promptVersion, body.reason, body.title, body.description, now, now + NINETY_DAYS_MS)
    .run();

  logRequest({ route: "/v1/reports", status: 202, latencyMs: Date.now() - started, promptVersion: body.promptVersion });
  return new Response(null, { status: 202 });
}

/** Daily cron sweep (wrangler.toml [triggers]): delete reports past their 90-day TTL. */
export async function sweepExpiredReports(env: Env): Promise<void> {
  await env.REPORTS_DB.prepare("DELETE FROM reports WHERE expiresAtMs < ?").bind(Date.now()).run();
}
