import type { RateLimiter } from "./durable-objects/RateLimiter.ts";
import type { BudgetCounter } from "./durable-objects/BudgetCounter.ts";

/** Worker bindings and vars (see wrangler.toml). */
export interface Env {
  AI: Ai;
  RATE_LIMITER: DurableObjectNamespace<RateLimiter>;
  BUDGET_COUNTER: DurableObjectNamespace<BudgetCounter>;
  IP_RATE_LIMITER: { limit(options: { key: string }): Promise<{ success: boolean }> };
  REPORTS_DB: D1Database;

  AI_MODEL_ID: string;
  AI_PRICE_PER_1K_TOKENS_USD: string;
  MONTHLY_BUDGET_USD: string;
  ENVIRONMENT?: "development" | "staging" | "production";

  /** Play Integrity service-account JSON key (secret, both envs). */
  INTEGRITY_SERVICE_ACCOUNT_JSON?: string;
  /** Staging only (ARCH §3.7); production must reject this header regardless of whether the secret is set. */
  DEBUG_KEY?: string;
}
