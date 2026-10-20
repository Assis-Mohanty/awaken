import { DurableObject } from "cloudflare:workers";
import type { Env } from "../env.ts";

/**
 * One instance per install ID (GD §6.6.7), routed via `env.RATE_LIMITER.getByName(installId)` so counters are
 * strongly consistent per install even under concurrent retries. "Per game-day"/"per ISO week" are measured in
 * UTC here — the proxy has no notion of the hunter's local day-start hour, that enforcement lives client-side
 * (§6.6.7 "the app also enforces locally"); this is the server-side safety net.
 *
 * `requestId` de-dup (ARCH §3.7 "the proxy doesn't charge quota twice for the same requestId"): a call is only
 * counted once per `requestId`; a WorkManager retry that reuses the same `requestId` is let through again
 * without consuming another slot.
 *
 * `manual` on WEEK_QUESTS distinguishes a user-triggered "Retry" from the automatic weekly trigger (GD §6.6.7:
 * 1 automatic + 2 manual retries per Path per ISO week). The wire contract has no field for this, so the
 * Android client (Phase 15) must send it as a header, `X-Retry-Kind: manual` — its absence means automatic.
 */
export class RateLimiter extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.ctx.storage.sql.exec(`
      CREATE TABLE IF NOT EXISTS calls (
        requestId TEXT PRIMARY KEY,
        kind TEXT NOT NULL,
        pathId TEXT,
        manual INTEGER NOT NULL,
        dayKey TEXT NOT NULL,
        weekKey TEXT NOT NULL,
        createdAtMs INTEGER NOT NULL
      )
    `);
  }

  async checkPlanPath(requestId: string): Promise<{ allowed: boolean }> {
    const now = Date.now();
    if (this.alreadyCounted(requestId)) return { allowed: true };

    const dayKey = utcDayKey(now);
    const weekKey = utcIsoWeekKey(now);
    const perDay = this.count("kind = 'PLAN_PATH' AND dayKey = ?", [dayKey]);
    const perWeek = this.count("kind = 'PLAN_PATH' AND weekKey = ?", [weekKey]);
    if (perDay >= 3 || perWeek >= 6) return { allowed: false };

    this.record({ requestId, kind: "PLAN_PATH", pathId: null, manual: false, dayKey, weekKey, createdAtMs: now });
    return { allowed: true };
  }

  async checkWeekQuests(params: { pathId: string; requestId: string; manual: boolean }): Promise<{ allowed: boolean }> {
    const { pathId, requestId, manual } = params;
    const now = Date.now();
    if (this.alreadyCounted(requestId)) return { allowed: true };

    const weekKey = utcIsoWeekKey(now);
    const limit = manual ? 2 : 1;
    const used = this.count("kind = 'WEEK_QUESTS' AND pathId = ? AND weekKey = ? AND manual = ?", [
      pathId,
      weekKey,
      manual ? 1 : 0,
    ]);
    if (used >= limit) return { allowed: false };

    this.record({ requestId, kind: "WEEK_QUESTS", pathId, manual, dayKey: utcDayKey(now), weekKey, createdAtMs: now });
    return { allowed: true };
  }

  private alreadyCounted(requestId: string): boolean {
    return this.ctx.storage.sql.exec("SELECT 1 FROM calls WHERE requestId = ?", requestId).toArray().length > 0;
  }

  private count(where: string, params: unknown[]): number {
    const row = this.ctx.storage.sql.exec<{ n: number }>(`SELECT COUNT(*) as n FROM calls WHERE ${where}`, ...params).one();
    return row.n;
  }

  private record(row: {
    requestId: string;
    kind: string;
    pathId: string | null;
    manual: boolean;
    dayKey: string;
    weekKey: string;
    createdAtMs: number;
  }): void {
    this.ctx.storage.sql.exec(
      "INSERT INTO calls (requestId, kind, pathId, manual, dayKey, weekKey, createdAtMs) VALUES (?, ?, ?, ?, ?, ?, ?)",
      row.requestId,
      row.kind,
      row.pathId,
      row.manual ? 1 : 0,
      row.dayKey,
      row.weekKey,
      row.createdAtMs,
    );
  }
}

function utcDayKey(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10);
}

/** ISO-8601 week key (e.g. "2026-W43"), UTC. */
function utcIsoWeekKey(ms: number): string {
  const date = new Date(ms);
  const d = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
  const dayNum = (d.getUTCDay() + 6) % 7; // Monday = 0
  d.setUTCDate(d.getUTCDate() - dayNum + 3); // nearest Thursday
  const firstThursday = new Date(Date.UTC(d.getUTCFullYear(), 0, 4));
  const week = 1 + Math.round(((d.getTime() - firstThursday.getTime()) / 86400000 - 3 + ((firstThursday.getUTCDay() + 6) % 7)) / 7);
  return `${d.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}
