import { DurableObject } from "cloudflare:workers";
import type { Env } from "../env.ts";

/**
 * A single global instance per environment (always `env.BUDGET_COUNTER.getByName("global")`), tracking estimated
 * USD spend this calendar month (UTC, resets on the 1st — ARCH §3.8 step 5 / GD §6.6.7 "Global: Monthly spend
 * cap"). Workers AI bills in "neurons", but `env.AI.run()` doesn't return a per-call neuron count — only token
 * usage is confirmed in the response shape — so this approximates spend from `usage.total_tokens` instead.
 * `AI_PRICE_PER_1K_TOKENS_USD` is a vars entry (not a secret): copy the configured model's $/1k-token rate from
 * https://developers.cloudflare.com/workers-ai/platform/pricing/ (verify before relying on it; it changes).
 *
 * Split in two calls because the pipeline needs to check the cap BEFORE calling the model (step 5, cheap checks
 * first) but can only know the real cost AFTER the model responds (it returns token/neuron usage) — `checkBudget`
 * is the pre-check, `addUsage` records the actual cost once known.
 */
export class BudgetCounter extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.ctx.storage.sql.exec(`
      CREATE TABLE IF NOT EXISTS spend (
        monthKey TEXT PRIMARY KEY,
        usdSpent REAL NOT NULL
      )
    `);
  }

  async checkBudget(): Promise<{ allowed: boolean; percentUsed: number }> {
    const { usdSpent, cap } = this.currentSpend();
    const percentUsed = cap > 0 ? (usdSpent / cap) * 100 : 0;
    return { allowed: usdSpent < cap, percentUsed };
  }

  async addUsage(tokens: number): Promise<{ percentUsed: number }> {
    const monthKey = utcMonthKey(Date.now());
    const pricePer1k = Number(this.env.AI_PRICE_PER_1K_TOKENS_USD);
    const addedUsd = (tokens / 1000) * pricePer1k;

    this.ctx.storage.sql.exec(
      `INSERT INTO spend (monthKey, usdSpent) VALUES (?, ?)
       ON CONFLICT(monthKey) DO UPDATE SET usdSpent = usdSpent + excluded.usdSpent`,
      monthKey,
      addedUsd,
    );

    const { usdSpent, cap } = this.currentSpend();
    const percentUsed = cap > 0 ? (usdSpent / cap) * 100 : 0;
    if (percentUsed >= 80) {
      // Dashboard alert hook (ARCH §7): a structured warn log Cloudflare Logpush/Analytics can alert on.
      console.warn(JSON.stringify({ ts: new Date().toISOString(), note: "budget_warning", percentUsed }));
    }
    return { percentUsed };
  }

  private currentSpend(): { usdSpent: number; cap: number } {
    const monthKey = utcMonthKey(Date.now());
    const row = this.ctx.storage.sql.exec<{ usdSpent: number }>("SELECT usdSpent FROM spend WHERE monthKey = ?", monthKey).toArray();
    const usdSpent = row[0]?.usdSpent ?? 0;
    const cap = Number(this.env.MONTHLY_BUDGET_USD);
    return { usdSpent, cap };
  }
}

function utcMonthKey(ms: number): string {
  const d = new Date(ms);
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}
